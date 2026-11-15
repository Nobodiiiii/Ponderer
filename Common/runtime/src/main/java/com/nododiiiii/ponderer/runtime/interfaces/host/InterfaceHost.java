package com.nododiiiii.ponderer.runtime.interfaces.host;

import com.nododiiiii.ponderer.runtime.interfaces.InterfaceExtensions;
import com.mojang.blaze3d.systems.RenderSystem;
import com.nododiiiii.ponderer.scene.interfaces.InterfaceDescriptor;
import com.nododiiiii.ponderer.scene.interfaces.InterfaceStepSpecs;
import com.nododiiiii.ponderer.runtime.interfaces.sandbox.SandboxSession;
import com.nododiiiii.ponderer.runtime.interfaces.sandbox.SessionOps;
import com.nododiiiii.ponderer.runtime.interfaces.InterfaceSlotOverlayRenderer;
import com.nododiiiii.ponderer.runtime.viewer.PonderRuntimeZLayers;
import com.nododiiiii.ponderer.runtime.viewer.PonderUiInteractionHelper;
import com.nododiiiii.ponderer.runtime.viewer.PonderUiPreviewAccess;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Owns the (at most one) visible interface session and routes PonderUI input to it.
 * Loader modules call the input methods from their screen events; a {@code true} return
 * means the event was consumed and must not reach PonderUI.
 *
 * <p>Modes (design doc 11.2): <b>watch</b> forwards only hover; <b>try</b> (entered from the
 * tab bar) forwards all input and pauses the timeline; <b>exercise</b> ({@code await_interface})
 * does the same until the goal is met. Every scripted mutation is recorded in a log, so
 * "reset" and leaving try mode rebuild the session and replay the script state.
 */
public final class InterfaceHost {

    public enum Mode { WATCH, TRY, EXERCISE }

    private record ScriptOp(@Nullable String key, Consumer<SandboxSession> apply) {
    }

    private static final int TAB_HEIGHT = 12;
    private static final int TAB_GAP = 2;

    @Nullable
    private static InterfaceOverlayElement current;
    private static Mode mode = Mode.WATCH;
    private static final List<ScriptOp> scriptLog = new ArrayList<>();

    @Nullable
    private static InterfaceStepSpecs.Goal exerciseGoal;
    @Nullable
    private static Component exerciseHint;
    private static int exerciseTicksLeft;

    // Ghost cursor of the running interface_action.
    @Nullable
    private static InterfaceStepSpecs.Action cursorAction;
    private static int cursorTick;
    private static int cursorPrevTick;
    private static int rippleTicks;
    @Nullable
    private static double[] cursorRest;

    private static int pressedButton = -1;
    private static double lastDragX;
    private static double lastDragY;

    record PreviewViewport(PonderUI screen, Rect2i bounds, int mouseX, int mouseY, float partialTicks) {}
    @Nullable private static PreviewViewport previewViewport;
    @Nullable private static PonderScene editorPreviewScene;


    private InterfaceHost() {
    }

    /** Shares the editor's local overlay coordinates with interfaces and their UI anchors. */
    public static void renderPreview(PonderUI screen, Rect2i bounds, int mouseX, int mouseY,
                                     float partialTicks, Runnable render) {
        PreviewViewport previous = previewViewport;
        previewViewport = new PreviewViewport(screen, bounds, mouseX, mouseY, partialTicks);
        try {
            render.run();
        } finally {
            previewViewport = previous;
        }
    }

    @Nullable
    static PreviewViewport previewViewport(PonderScene scene) {
        return previewViewport != null && previewViewport.screen().getActiveScene() == scene ? previewViewport : null;
    }

    /** Allow sandbox instructions only for this editor's scene while seeking or playing it. */
    public static void advancePreview(PonderScene scene, Runnable advance) {
        PonderScene previous = editorPreviewScene;
        editorPreviewScene = scene;
        try {
            advance.run();
        } finally {
            editorPreviewScene = previous;
        }
    }

    public static boolean isPlaybackSuppressed(PonderScene scene) {
        return com.nododiiiii.ponderer.runtime.api.PlaybackExecutionContext.isActive() && editorPreviewScene != scene;
    }

    /** Editor previews have no live PonderUI screen to receive the normal client tick. */
    public static void tickPreview(PonderScene scene) {
        SandboxSession session = sessionOf(scene);
        if (session != null) {
            session.tick();
            if (rippleTicks > 0) rippleTicks--;
        }
    }

    // ---- Lifecycle ----------------------------------------------------------------------

    /** Opens {@code descriptor} for {@code scene}, replacing any interface that is currently shown. */
    public static SandboxSession open(PonderScene scene, InterfaceDescriptor descriptor) {
        com.nododiiiii.ponderer.runtime.api.RuntimeFeatures.requireInterfaces("InterfaceHost.java");
        closeAll("replaced");
        SandboxSession session = SandboxSession.open(descriptor, scene.getWorld());
        InterfaceOverlayElement element = new InterfaceOverlayElement(scene, session);
        current = element;
        scene.addElement(element);
        return session;
    }

    public static SandboxSession openPanel(PonderScene scene, InterfaceDescriptor descriptor,
                                          net.minecraft.world.phys.Vec3 anchor,
                                          net.createmod.catnip.math.Pointing direction, List<Double> transform,
                                          @Nullable List<Double> crop, boolean background) {
        SandboxSession session = open(scene, descriptor);
        current.setPanel(new InterfacePanelLayout(anchor, direction, transform, crop, background));
        return session;
    }

    /** Hides the interface shown by {@code scene}, if any ({@code hide_interface}, a later {@code show_structure}). */
    public static void hide(PonderScene scene) {
        InterfaceOverlayElement element = current;
        if (element != null && element.scene() == scene) {
            element.setVisible(false);
            closeAll("hidden by scene");
        }
    }

    public static void closeAll(String reason) {
        cancelSlotDrag();
        InterfaceOverlayElement element = current;
        current = null;
        mode = Mode.WATCH;
        scriptLog.clear();
        exerciseGoal = null;
        exerciseHint = null;
        cursorAction = null;
        cursorRest = null;
        resetInput();
        InterfaceSlotOverlayRenderer.clearRuntimeBindings();
        if (element != null) {
            element.session().destroy(reason);
        }
    }

    static void onElementReset(InterfaceOverlayElement element) {
        if (current == element) {
            closeAll("scene reset");
        } else {
            element.session().destroy("scene reset");
        }
    }

    static boolean isCurrent(InterfaceOverlayElement element) {
        return current == element;
    }

    /** Client tick: ticks the visible session, holds the timeline outside watch mode, drops stale sessions. */
    public static void tick() {
        InterfaceOverlayElement element = current;
        if (element == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen == null) {
            closeAll(mc.level == null ? "left world" : "host screen closed");
            return;
        }
        if (!(mc.screen instanceof PonderUI ui) || ui.getActiveScene() != element.scene()) {
            return;
        }
        if (ui instanceof PonderUiPreviewAccess previewAccess && previewAccess.ponderer$getFreePreview().isActive()) {
            return;
        }
        SandboxSession session = element.session();
        session.tick();
        if (rippleTicks > 0) {
            rippleTicks--;
        }
        if (mode == Mode.EXERCISE) {
            InterfaceStepSpecs.Goal goal = exerciseGoal;
            if (goal == null || !session.canInteract() || SessionOps.goalMet(session, goal) || --exerciseTicksLeft <= 0) {
                endExercise();
            }
        }
        if (mode != Mode.WATCH) {
            // Holds the scene clock like a skip does; public API (PonderUI#coolDownAfterSkip).
            ui.coolDownAfterSkip();
        }
    }

    // ---- Script API (timeline instructions) ---------------------------------------------

    /**
     * Applies a scripted mutation to the session shown by {@code scene} and records it so a
     * reset can replay it. A non-null {@code key} replaces an earlier op with the same key
     * (e.g. successive values of one data slot).
     */
    public static void script(PonderScene scene, @Nullable String key, Consumer<SandboxSession> op) {
        InterfaceOverlayElement element = current;
        if (element == null || element.scene() != scene || element.session().isDestroyed()) {
            return;
        }
        if (key != null) {
            scriptLog.removeIf(existing -> key.equals(existing.key()));
        }
        scriptLog.add(new ScriptOp(key, op));
        op.accept(element.session());
    }

    /** Drops earlier keyed ops whose key starts with {@code prefix} (replace-style slot steps). */
    public static void forgetScript(PonderScene scene, String prefix) {
        InterfaceOverlayElement element = current;
        if (element != null && element.scene() == scene) {
            scriptLog.removeIf(existing -> existing.key() != null && existing.key().startsWith(prefix));
        }
    }

    @Nullable
    public static SandboxSession sessionOf(PonderScene scene) {
        InterfaceOverlayElement element = current;
        return element == null || element.scene() != scene || element.session().isDestroyed() ? null : element.session();
    }

    /** Starts the ghost cursor for {@code action}; the instruction drives it with {@link #tickCursor}. */
    public static void startCursor(PonderScene scene, InterfaceStepSpecs.Action action) {
        if (sessionOf(scene) == null) {
            return;
        }
        cursorAction = action;
        cursorTick = 0;
        cursorPrevTick = 0;
    }

    public static void tickCursor(PonderScene scene, InterfaceStepSpecs.Action action, int tick) {
        if (cursorAction != action) {
            return;
        }
        cursorPrevTick = cursorTick;
        cursorTick = tick;
    }

    public static void pressRipple() {
        rippleTicks = 6;
    }

    public static void endCursor(PonderScene scene, InterfaceStepSpecs.Action action) {
        if (cursorAction != action) {
            return;
        }
        SandboxSession session = sessionOf(scene);
        cursorRest = session == null ? null : cursorEnd(session, action);
        cursorAction = null;
    }

    /** Enters exercise mode for {@code await_interface}; skipped when the session cannot take input. */
    public static void beginExercise(PonderScene scene, InterfaceStepSpecs.Goal goal, @Nullable Component hint, int timeout) {
        SandboxSession session = sessionOf(scene);
        if (session == null || !session.canInteract() || SessionOps.goalMet(session, goal)) {
            return;
        }
        mode = Mode.EXERCISE;
        exerciseGoal = goal;
        exerciseHint = hint;
        exerciseTicksLeft = timeout <= 0 ? Integer.MAX_VALUE : timeout;
    }

    private static void endExercise() {
        // The player's changes are the point of an exercise, so they are kept.
        mode = Mode.WATCH;
        exerciseGoal = null;
        exerciseHint = null;
        resetInput();
    }

    // ---- Try mode -----------------------------------------------------------------------

    public static Mode mode() {
        return mode;
    }

    public static void enterTry() {
        InterfaceOverlayElement element = current;
        if (element != null && element.session().canInteract() && mode == Mode.WATCH) {
            mode = Mode.TRY;
            cursorAction = null;
        }
    }

    public static void exitTry() {
        if (mode == Mode.TRY) {
            resetToScript();
            mode = Mode.WATCH;
        }
    }

    /** Rebuilds the session from its descriptor and replays the scripted ops, discarding the player's changes. */
    public static void resetToScript() {
        InterfaceOverlayElement element = current;
        if (element == null) {
            return;
        }
        SandboxSession old = element.session();
        old.destroy("reset to script");
        SandboxSession fresh = SandboxSession.open(old.descriptor(), element.scene().getWorld());
        element.replaceSession(fresh);
        resetInput();
        for (ScriptOp op : List.copyOf(scriptLog)) {
            op.apply().accept(fresh);
        }
    }

    // ---- Queries ------------------------------------------------------------------------

    /** The session shown on {@code screen}'s active scene, if any. */
    @Nullable
    public static SandboxSession sessionFor(@Nullable Screen screen) {
        InterfaceOverlayElement element = current;
        if (element == null || element.session().isDestroyed() || !element.isVisible()) {
            return null;
        }
        Screen host = previewViewport == null ? screen : previewViewport.screen();
        if (!(host instanceof PonderUI ui) || ui.getActiveScene() != element.scene()) {
            return null;
        }
        if (ui instanceof PonderUiPreviewAccess previewAccess && previewAccess.ponderer$getFreePreview().isActive()) {
            return null;
        }
        return element.session();
    }

    /** The embedded screen currently shown in PonderUI, used by UI anchors and JEI handlers. */
    @Nullable
    public static Screen activeScreen() {
        SandboxSession session = sessionFor(Minecraft.getInstance().screen);
        return session == null ? null : session.currentScreen();
    }

    /** GUI rectangle {left, top, width, height} of the interface on the current PonderUI, if any. */
    @Nullable
    public static int[] activeRect() {
        SandboxSession session = sessionFor(Minecraft.getInstance().screen);
        return session == null ? null : current.displayRect();
    }

    /** Maps a native menu rectangle to the visible panel (identity for a standalone interface). */
    public static int[] toScreenRect(int x, int y, int width, int height) {
        InterfaceOverlayElement element = current;
        if (element == null) return new int[]{x, y, width, height};
        double[] start = element.toDisplay(x, y), end = element.toDisplay(x + width, y + height);
        int left = (int) Math.floor(start[0]), top = (int) Math.floor(start[1]);
        return new int[]{left, top, Math.max(1, (int) Math.ceil(end[0]) - left), Math.max(1, (int) Math.ceil(end[1]) - top)};
    }

    public static boolean isNativeRectVisible(int x, int y, int width, int height) {
        return current == null || current.isNativeRectVisible(x, y, width, height);
    }

    /** Whether real input currently goes to the sandbox (try or exercise mode). */
    public static boolean isTakingInput(@Nullable SandboxSession session) {
        return session != null && mode != Mode.WATCH && session.canInteract();
    }

    // ---- Input --------------------------------------------------------------------------

    public static boolean mouseClicked(Screen screen, double x, double y, int button) {
        SandboxSession session = sessionFor(screen);
        if (session == null || PonderUiInteractionHelper.hasPriorityPonderButtonAt(screen, x, y)) {
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && clickTab(session, x, y)) {
            return true;
        }
        if (InterfaceExtensions.editing().isActive()) {
            if (current.clipsInputAt(x, y)) return false;
            double[] local = current.toNative(x, y);
            return InterfaceExtensions.editing().pressed(session, local[0], local[1], button);
        }
        if (!isInside(session, x, y) || !isTakingInput(session)) {
            return false;
        }
        double[] local = current.toNative(x, y);
        session.mouseClicked(local[0], local[1], button);
        pressedButton = button;
        lastDragX = x;
        lastDragY = y;
        return true;
    }

    public static boolean mouseReleased(Screen screen, double x, double y, int button) {
        SandboxSession session = sessionFor(screen);
        if (session == null) {
            resetInput();
            return false;
        }
        if (InterfaceExtensions.editing().isActive()) {
            double[] local = current.clipsInputAt(x, y) ? new double[]{-1, -1} : current.toNative(x, y);
            return InterfaceExtensions.editing().released(session, local[0], local[1], button);
        }
        if (pressedButton != button) {
            return false;
        }
        pressedButton = -1;
        double[] local = current.clipsInputAt(x, y) ? new double[]{-1, -1} : current.toNative(x, y);
        session.mouseReleased(local[0], local[1], button);
        return true;
    }

    public static boolean mouseScrolled(Screen screen, double x, double y, double scrollX, double scrollY) {
        SandboxSession session = sessionFor(screen);
        if (session == null || !isInside(session, x, y) || InterfaceExtensions.editing().isActive()) {
            return false;
        }
        if (isTakingInput(session)) {
            double[] local = current.toNative(x, y);
            session.mouseScrolled(local[0], local[1], scrollX, scrollY);
        }
        // Scrolling over the interface must not switch Ponder scenes.
        return true;
    }

    public static boolean keyPressed(Screen screen, int key, int scanCode, int modifiers) {
        if (screen instanceof PonderUI && key == GLFW.GLFW_KEY_SPACE && !InterfaceExtensions.editing().isActive()) {
            return false;
        }
        SandboxSession session = sessionFor(screen);
        if (session == null || InterfaceExtensions.editing().isActive()) {
            return false;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (session.popScreen()) {
                return true;
            }
            if (mode == Mode.TRY) {
                exitTry();
                return true;
            }
            if (mode == Mode.EXERCISE) {
                endExercise();
                return true;
            }
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!isTakingInput(session) || !isInside(session, mouseX(mc), mouseY(mc))) {
            return false;
        }
        return session.keyPressed(key, scanCode, modifiers);
    }

    public static boolean charTyped(Screen screen, char c, int modifiers) {
        SandboxSession session = sessionFor(screen);
        if (!isTakingInput(session) || InterfaceExtensions.editing().isActive()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        return isInside(session, mouseX(mc), mouseY(mc)) && session.charTyped(c, modifiers);
    }

    /** Whether hover (tooltips, slot highlight) should follow the real cursor this frame. */
    static boolean shouldForwardHover(PonderUI ui, int mouseX, int mouseY) {
        return !PonderUiInteractionHelper.hasPriorityPonderButtonAt(ui, mouseX, mouseY)
            && (current == null || !current.clipsInputAt(mouseX, mouseY));
    }

    /**
     * Fabric has no screen drag event, so drags are synthesized for both loaders from cursor
     * movement while a forwarded button is held.
     */
    static void beforeRender(SandboxSession session, int mouseX, int mouseY) {
        InterfaceExtensions.editing().applyPreview(session);
        if (pressedButton < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double x = preciseMouseX(mc);
        double y = preciseMouseY(mc);
        if (x != lastDragX || y != lastDragY) {
            if (!current.clipsInputAt(x, y)) {
                double[] local = current.toNative(x, y);
                double[] previous = current.toNative(lastDragX, lastDragY);
                session.mouseDragged(local[0], local[1], pressedButton, local[0] - previous[0], local[1] - previous[1]);
            }
            lastDragX = x;
            lastDragY = y;
        }
    }

    private static boolean isInside(SandboxSession session, double x, double y) {
        int[] rect = activeRect();
        return rect != null && x >= rect[0] && y >= rect[1] && x < rect[0] + rect[2] && y < rect[1] + rect[3];
    }

    private static void resetInput() {
        pressedButton = -1;
        InterfaceExtensions.editing().resetInput();
    }

    static int mouseX(Minecraft mc) {
        return (int) preciseMouseX(mc);
    }

    static int mouseY(Minecraft mc) {
        return (int) preciseMouseY(mc);
    }

    private static double preciseMouseX(Minecraft mc) {
        return mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / Math.max(1, mc.getWindow().getScreenWidth());
    }

    private static double preciseMouseY(Minecraft mc) {
        return mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / Math.max(1, mc.getWindow().getScreenHeight());
    }

    // ---- Tab bar ("▶ Try / ⟲ Reset / ✕ Exit") -------------------------------------------

    private record Tab(Component label, Runnable action, int x, int y, int width) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + width && my < y + TAB_HEIGHT;
        }
    }

    private static List<Tab> tabs(SandboxSession session) {
        List<Tab> tabs = new ArrayList<>();
        int[] rect = activeRect();
        if (rect == null || InterfaceExtensions.editing().isActive()) {
            return tabs;
        }
        List<Component> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        switch (mode) {
            case WATCH -> {
                if (session.canInteract()) {
                    labels.add(Component.translatable("ponderer.ui.interface.try"));
                    actions.add(InterfaceHost::enterTry);
                }
            }
            case TRY -> {
                labels.add(Component.translatable("ponderer.ui.interface.reset"));
                actions.add(InterfaceHost::resetToScript);
                labels.add(Component.translatable("ponderer.ui.interface.exit"));
                actions.add(InterfaceHost::exitTry);
            }
            case EXERCISE -> {
                labels.add(Component.translatable("ponderer.ui.interface.skip"));
                actions.add(InterfaceHost::endExercise);
            }
        }
        Font font = Minecraft.getInstance().font;
        int right = rect[0] + rect[2];
        int y = Math.max(0, rect[1] - TAB_HEIGHT - TAB_GAP);
        for (int i = labels.size() - 1; i >= 0; i--) {
            int width = font.width(labels.get(i)) + 8;
            right -= width;
            tabs.add(0, new Tab(labels.get(i), actions.get(i), right, y, width));
            right -= TAB_GAP;
        }
        return tabs;
    }

    private static boolean clickTab(SandboxSession session, double x, double y) {
        for (Tab tab : tabs(session)) {
            if (tab.contains(x, y)) {
                tab.action().run();
                return true;
            }
        }
        return false;
    }

    static void renderChrome(GuiGraphics graphics, SandboxSession session, int mouseX, int mouseY, float partialTicks) {
        Font font = Minecraft.getInstance().font;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, PonderRuntimeZLayers.INTERFACE_OVERLAY_LAYER);
        RenderSystem.disableDepthTest();
        for (Tab tab : tabs(session)) {
            boolean hovered = tab.contains(mouseX, mouseY);
            graphics.fill(tab.x(), tab.y(), tab.x() + tab.width(), tab.y() + TAB_HEIGHT, hovered ? 0xF0404058 : 0xE0101018);
            graphics.renderOutline(tab.x(), tab.y(), tab.width(), TAB_HEIGHT, 0xFF7A7AA0);
            graphics.drawString(font, tab.label(), tab.x() + 4, tab.y() + 2, 0xFFFFFF, false);
        }
        int[] rect = activeRect();
        Component banner = switch (mode) {
            case TRY -> Component.translatable("ponderer.ui.interface.try_notice").withStyle(ChatFormatting.GRAY);
            case EXERCISE -> exerciseHint == null
                ? Component.translatable("ponderer.ui.interface.exercise").withStyle(ChatFormatting.YELLOW)
                : exerciseHint.copy().withStyle(ChatFormatting.YELLOW);
            case WATCH -> null;
        };
        if (banner != null && rect != null) {
            int width = font.width(banner) + 8;
            int x = rect[0];
            int y = Math.max(0, rect[1] - TAB_HEIGHT - TAB_GAP);
            graphics.fill(x, y, x + width, y + TAB_HEIGHT, 0xE0101018);
            graphics.drawString(font, banner, x + 4, y + 2, 0xFFFFFF, false);
        }
        renderCursor(graphics, session, partialTicks);
        graphics.pose().popPose();
    }

    // ---- Ghost cursor -------------------------------------------------------------------

    /** The ghost cursor position while an action animates in watch mode, else null. */
    @Nullable
    static double[] cursorPosition(SandboxSession session, float partialTicks) {
        InterfaceStepSpecs.Action action = cursorAction;
        if (action == null || mode != Mode.WATCH || !action.cursor() || action.duration() <= 0) {
            return null;
        }
        double[] end = cursorEnd(session, action);
        double[] start = cursorRest != null ? cursorRest : center(session);
        if (end == null || start == null) {
            return null;
        }
        float t = Mth.clamp((Mth.lerp(partialTicks, cursorPrevTick, cursorTick)) / (float) action.duration(), 0, 1);
        if (action.kind() == InterfaceStepSpecs.ActionKind.DRAG) {
            double[] from = SessionOps.point(session, action.target());
            if (from != null) {
                return t < DRAG_PICK ? lerp(start, from, ease(t / DRAG_PICK))
                    : lerp(from, end, ease((t - DRAG_PICK) / (1 - DRAG_PICK)));
            }
        }
        // Arrive at the target a little early and rest there while the click lands.
        return lerp(start, end, ease(Math.min(1, t / 0.8f)));
    }

    /** Fraction of a drag spent moving to the source slot before the stack is picked up. */
    public static final float DRAG_PICK = 0.4f;

    @Nullable
    private static double[] cursorEnd(SandboxSession session, InterfaceStepSpecs.Action action) {
        if (action.kind() == InterfaceStepSpecs.ActionKind.DRAG && action.to() != null) {
            return SessionOps.point(session, action.to());
        }
        return SessionOps.point(session, action.target());
    }

    @Nullable
    private static double[] center(SandboxSession session) {
        int[] rect = session.guiRect();
        return rect == null ? null : new double[]{rect[0] + rect[2] / 2.0, rect[1] + rect[3] / 2.0};
    }

    private static double[] lerp(double[] a, double[] b, float t) {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
    }

    private static float ease(float t) {
        return t * t * (3 - 2 * t);
    }

    static void renderCursor(GuiGraphics graphics, SandboxSession session, float partialTicks) {
        double[] p = cursorPosition(session, partialTicks);
        if (p == null || !isNativeRectVisible((int) p[0], (int) p[1], 1, 1)) {
            return;
        }
        if (current != null) p = current.toDisplay(p[0], p[1]);
        int x = (int) p[0];
        int y = (int) p[1];
        if (rippleTicks > 0) {
            int r = 2 + (6 - rippleTicks) * 2;
            graphics.renderOutline(x - r, y - r, r * 2, r * 2, 0xA0FFFFFF);
        }
        // Simple arrow: a white wedge with a dark outline.
        for (int row = 0; row < 10; row++) {
            int len = Math.min(row, 10 - row / 2) / 2 + 1;
            graphics.fill(x - 1, y + row - 1, x + len + 1, y + row + 1, 0xFF202020);
        }
        for (int row = 0; row < 9; row++) {
            int len = Math.min(row, 9 - row / 2) / 2;
            graphics.fill(x, y + row, x + len, y + row + 1, 0xFFFFFFFF);
        }
    }

    public static void cancelSlotDrag() { InterfaceExtensions.editing().cancelDrag(); }
    static void renderDraggedBinding(GuiGraphics graphics, int x, int y) { InterfaceExtensions.editing().renderDragged(graphics, x, y); }

}
