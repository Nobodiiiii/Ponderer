package com.nododiiiii.ponderer.interfaces.host;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.interfaces.model.InterfaceDescriptor;
import com.nododiiiii.ponderer.interfaces.model.InterfaceStepSpecs;
import com.nododiiiii.ponderer.interfaces.sandbox.SandboxSession;
import com.nododiiiii.ponderer.interfaces.sandbox.SessionOps;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ui.InterfaceSlotEditState;
import com.nododiiiii.ponderer.ui.InterfaceSlotOverlayRenderer;
import com.nododiiiii.ponderer.ui.PonderRuntimeZLayers;
import com.nododiiiii.ponderer.ui.PonderUiInteractionHelper;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.Slot;
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

    @Nullable
    private static DslScene.InterfaceSlotBinding draggedBinding;
    @Nullable
    private static Integer draggedBindingOrigin;

    private InterfaceHost() {
    }

    // ---- Lifecycle ----------------------------------------------------------------------

    /** Opens {@code descriptor} for {@code scene}, replacing any interface that is currently shown. */
    public static SandboxSession open(PonderScene scene, InterfaceDescriptor descriptor) {
        closeAll("replaced");
        SandboxSession session = SandboxSession.open(descriptor, scene.getWorld());
        InterfaceOverlayElement element = new InterfaceOverlayElement(scene, session);
        current = element;
        scene.addElement(element);
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
        if (!(screen instanceof PonderUI ui) || ui.getActiveScene() != element.scene()) {
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
        return session == null ? null : session.guiRect();
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
        if (InterfaceSlotEditState.isActive()) {
            return editPressed(session, x, y, button);
        }
        if (!isInside(session, x, y) || !isTakingInput(session)) {
            return false;
        }
        session.mouseClicked(x, y, button);
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
        if (InterfaceSlotEditState.isActive()) {
            return editReleased(session, x, y, button);
        }
        if (pressedButton != button) {
            return false;
        }
        pressedButton = -1;
        session.mouseReleased(x, y, button);
        return true;
    }

    public static boolean mouseScrolled(Screen screen, double x, double y, double scrollX, double scrollY) {
        SandboxSession session = sessionFor(screen);
        if (session == null || !isInside(session, x, y) || InterfaceSlotEditState.isActive()) {
            return false;
        }
        if (isTakingInput(session)) {
            session.mouseScrolled(x, y, scrollX, scrollY);
        }
        // Scrolling over the interface must not switch Ponder scenes.
        return true;
    }

    public static boolean keyPressed(Screen screen, int key, int scanCode, int modifiers) {
        SandboxSession session = sessionFor(screen);
        if (session == null || InterfaceSlotEditState.isActive()) {
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
        if (!isTakingInput(session) || InterfaceSlotEditState.isActive()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        return isInside(session, mouseX(mc), mouseY(mc)) && session.charTyped(c, modifiers);
    }

    /** Whether hover (tooltips, slot highlight) should follow the real cursor this frame. */
    static boolean shouldForwardHover(PonderUI ui, int mouseX, int mouseY) {
        return !PonderUiInteractionHelper.hasPriorityPonderButtonAt(ui, mouseX, mouseY);
    }

    /**
     * Fabric has no screen drag event, so drags are synthesized for both loaders from cursor
     * movement while a forwarded button is held.
     */
    static void beforeRender(SandboxSession session, int mouseX, int mouseY) {
        if (pressedButton < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double x = preciseMouseX(mc);
        double y = preciseMouseY(mc);
        if (x != lastDragX || y != lastDragY) {
            session.mouseDragged(x, y, pressedButton, x - lastDragX, y - lastDragY);
            lastDragX = x;
            lastDragY = y;
        }
    }

    private static boolean isInside(SandboxSession session, double x, double y) {
        int[] rect = session.guiRect();
        return rect != null && x >= rect[0] && y >= rect[1] && x < rect[0] + rect[2] && y < rect[1] + rect[3];
    }

    private static void resetInput() {
        pressedButton = -1;
        draggedBinding = null;
        draggedBindingOrigin = null;
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
        int[] rect = session.guiRect();
        if (rect == null || InterfaceSlotEditState.isActive()) {
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
        int[] rect = session.guiRect();
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

    private static void renderCursor(GuiGraphics graphics, SandboxSession session, float partialTicks) {
        double[] p = cursorPosition(session, partialTicks);
        if (p == null) {
            return;
        }
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

    // ---- change_interface_slot editing (JEI ghost bindings over slots) ------------------

    private static boolean editPressed(SandboxSession session, double x, double y, int button) {
        Screen screen = session.currentScreen();
        Slot slot = screen == null ? null : InterfaceSlotOverlayRenderer.findSlotAt(screen, x, y);
        if (slot == null) {
            return isInside(session, x, y);
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            InterfaceSlotEditState.removeBinding(slot.index);
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            DslScene.InterfaceSlotBinding binding = InterfaceSlotEditState.getBinding(slot.index);
            if (binding != null) {
                draggedBinding = binding;
                draggedBindingOrigin = binding.slotIndex;
                InterfaceSlotEditState.removeBinding(slot.index);
            }
        }
        return true;
    }

    private static boolean editReleased(SandboxSession session, double x, double y, int button) {
        Screen screen = session.currentScreen();
        Slot slot = screen == null ? null : InterfaceSlotOverlayRenderer.findSlotAt(screen, x, y);
        DslScene.InterfaceSlotBinding dragged = draggedBinding;
        if (dragged != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (slot != null) {
                InterfaceSlotEditState.putBinding(slot.index, slot.x, slot.y, dragged.ingredientId, dragged.ingredientKind);
            } else if (draggedBindingOrigin != null) {
                InterfaceSlotEditState.putBinding(draggedBindingOrigin, dragged.slotX, dragged.slotY,
                    dragged.ingredientId, dragged.ingredientKind);
            }
            draggedBinding = null;
            draggedBindingOrigin = null;
            return true;
        }
        return slot != null || isInside(session, x, y);
    }

    static void renderDraggedBinding(GuiGraphics graphics, int mouseX, int mouseY) {
        DslScene.InterfaceSlotBinding dragged = draggedBinding;
        if (dragged == null) {
            return;
        }
        var element = JeiCompat.resolveIngredientById(dragged.ingredientId, dragged.ingredientKind);
        if (element == null) {
            return;
        }
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, PonderRuntimeZLayers.INTERFACE_OVERLAY_LAYER);
        RenderSystem.disableDepthTest();
        element.render(graphics, mouseX - 8, mouseY - 8);
        graphics.pose().popPose();
        graphics.flush();
    }
}
