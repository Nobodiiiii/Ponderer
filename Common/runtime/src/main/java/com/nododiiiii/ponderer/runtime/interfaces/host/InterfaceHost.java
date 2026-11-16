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
import java.util.function.Supplier;

/**
 * Owns visible interface sessions and routes PonderUI input to the topmost hit panel.
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

    /** Stable timeline identity; its live session is rebuilt when seeking or replaying. */
    public static final class Target {
        private InterfaceOverlayElement element;
        private boolean panel;
        private Mode mode = Mode.WATCH;
        private final List<ScriptOp> scriptLog = new ArrayList<>();
        private InterfaceStepSpecs.Goal exerciseGoal;
        private Component exerciseHint;
        private int exerciseTicksLeft;
        private InterfaceStepSpecs.Action cursorAction;
        private int cursorTick, cursorPrevTick, rippleTicks;
        private double[] cursorRest;
        private int pressedButton = -1;
        private double lastDragX, lastDragY;
    }

    private static final List<Target> targets = new ArrayList<>();
    @Nullable private static Target scopedTarget;
    @Nullable private static Target focusedTarget;

    private static Target target() {
        if (scopedTarget != null) return scopedTarget;
        for (Target target : targets) if (!target.element.isPanel()) return target;
        return targets.isEmpty() ? null : targets.getLast();
    }

    @Nullable private static InterfaceOverlayElement current() {
        Target target = target();
        return target == null ? null : target.element;
    }

    public static <T> T withTarget(Target target, Supplier<T> action) {
        Target previous = scopedTarget;
        scopedTarget = target;
        try { return action.get(); }
        finally { scopedTarget = previous; }
    }

    public static void withTarget(Target target, Runnable action) {
        withTarget(target, () -> { action.run(); return null; });
    }

    static Target targetOf(InterfaceOverlayElement element) {
        for (Target target : targets) if (target.element == element) return target;
        return null;
    }

    private static Target targetOf(SandboxSession session) {
        for (Target target : targets) if (target.element.session() == session) return target;
        return null;
    }

    public static boolean isOpen(Target target, PonderScene scene) {
        return target != null && target.element != null && target.element.scene() == scene
            && !target.element.session().isDestroyed() && targets.contains(target);
    }

    public static Mode mode(Target target) { return target == null ? Mode.WATCH : target.mode; }

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
        for (Target target : List.copyOf(targets)) if (isOpen(target, scene))
            withTarget(target, () -> tickSession(null));
    }

    // ---- Lifecycle ----------------------------------------------------------------------

    /** Opens {@code descriptor} for {@code scene}, replacing any interface that is currently shown. */
    public static SandboxSession open(PonderScene scene, InterfaceDescriptor descriptor) {
        com.nododiiiii.ponderer.runtime.api.RuntimeFeatures.requireInterfaces("InterfaceHost.java");
        Target owner = scopedTarget == null ? new Target() : scopedTarget;
        closeAll("replaced");
        owner.panel = false;
        return openTarget(owner, scene, descriptor);
    }

    private static SandboxSession openTarget(Target owner, PonderScene scene, InterfaceDescriptor descriptor) {
        SandboxSession session = SandboxSession.open(descriptor, scene.getWorld());
        InterfaceOverlayElement element = new InterfaceOverlayElement(scene, session);
        owner.element = element;
        targets.add(owner);
        if (focusedTarget == null) focusedTarget = owner;
        scene.addElement(element);
        return session;
    }

    public static SandboxSession openPanel(PonderScene scene, InterfaceDescriptor descriptor,
                                          net.minecraft.world.phys.Vec3 anchor,
                                          net.createmod.catnip.math.Pointing direction, List<Double> transform,
                                          @Nullable List<Double> crop, boolean background) {
        return openPanel(scene, descriptor, anchor, direction, transform, crop, background, true);
    }

    public static SandboxSession openPanel(PonderScene scene, InterfaceDescriptor descriptor,
                                          net.minecraft.world.phys.Vec3 anchor,
                                          net.createmod.catnip.math.Pointing direction, List<Double> transform,
                                          @Nullable List<Double> crop, boolean background, boolean blocking) {
        return openPanel(scene, descriptor, anchor, direction, transform, crop, background, blocking, false);
    }

    public static SandboxSession openPanel(PonderScene scene, InterfaceDescriptor descriptor,
                                          net.minecraft.world.phys.Vec3 anchor,
                                          net.createmod.catnip.math.Pointing direction, List<Double> transform,
                                          @Nullable List<Double> crop, boolean background, boolean blocking, boolean animation) {
        com.nododiiiii.ponderer.runtime.api.RuntimeFeatures.requireInterfaces("InterfaceHost.java");
        Target owner = scopedTarget == null ? new Target() : scopedTarget;
        // Panels coexist; only a new standalone interface replaces the scene's interfaces.
        for (Target existing : List.copyOf(targets))
            if (existing == owner || existing.element.scene() != scene) close(existing, "replaced");
        owner.panel = true;
        SandboxSession session = openTarget(owner, scene, descriptor);
        owner.element.setPanel(new InterfacePanelLayout(anchor, direction, transform, crop, background), blocking, animation);
        return session;
    }

    /** A transition must never animate a later interface that replaced its own session. */
    public static void setPanelFade(PonderScene scene, SandboxSession session, float progress) {
        Target owner = targetOf(session);
        InterfaceOverlayElement element = owner == null ? null : owner.element;
        if (element != null && element.scene() == scene && element.session() == session && element.isPanel()) {
            element.setFade(progress);
        }
    }

    /** Hides the interface shown by {@code scene}, if any ({@code hide_interface}, a later {@code show_structure}). */
    public static void hide(PonderScene scene) {
        if (scopedTarget == null || !scopedTarget.panel) {
            for (Target owner : List.copyOf(targets)) if (owner.element.scene() == scene) close(owner, "scene hidden");
            return;
        }
        Target owner = target();
        if (isOpen(owner, scene)) close(owner, "hidden by scene");
    }

    public static void closeAll(String reason) {
        cancelSlotDrag();
        for (Target owner : List.copyOf(targets)) close(owner, reason);
    }

    private static void close(Target owner, String reason) {
        if (owner == null || !targets.remove(owner)) return;
        InterfaceOverlayElement element = owner.element;
        if (InterfaceExtensions.editing().ownsSession(element.session())) cancelSlotDrag();
        owner.element = null;
        owner.mode = Mode.WATCH;
        owner.scriptLog.clear();
        owner.exerciseGoal = null;
        owner.exerciseHint = null;
        owner.cursorAction = null;
        owner.cursorRest = null;
        owner.pressedButton = -1;
        owner.rippleTicks = 0;
        if (focusedTarget == owner) focusedTarget = null;
        InterfaceSlotOverlayRenderer.clearRuntimeBindings(element.session());
        element.setVisible(false);
        element.session().destroy(reason);
    }

    static void onElementReset(InterfaceOverlayElement element) {
        Target owner = targetOf(element);
        if (owner != null) close(owner, "scene reset");
        else element.session().destroy("scene reset");
    }

    static boolean isCurrent(InterfaceOverlayElement element) {
        return targetOf(element) != null;
    }

    static boolean rendersInterfaces(InterfaceOverlayElement element) {
        return !targets.isEmpty() && targets.getFirst().element == element;
    }

    static void renderInterfaces(PonderScene scene, PonderUI screen, GuiGraphics graphics, float partialTicks) {
        for (Target owner : List.copyOf(targets)) {
            if (isOpen(owner, scene) && owner.element.isVisible())
                withTarget(owner, () -> owner.element.renderInterface(scene, screen, graphics, partialTicks));
        }
    }

    /** Client tick: ticks the visible session, holds the timeline outside watch mode, drops stale sessions. */
    public static void tick() {
        if (targets.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen == null) {
            closeAll(mc.level == null ? "left world" : "host screen closed");
            return;
        }
        if (!(mc.screen instanceof PonderUI ui)) return;
        if (ui instanceof PonderUiPreviewAccess previewAccess && previewAccess.ponderer$getFreePreview().isActive()) {
            return;
        }
        for (Target owner : List.copyOf(targets)) {
            if (owner.element.scene() != ui.getActiveScene()) close(owner, "scene changed");
            else withTarget(owner, () -> tickSession(ui));
        }
    }

    private static void tickSession(@Nullable PonderUI ui) {
        Target owner = target();
        InterfaceOverlayElement element = current();
        SandboxSession session = element.session();
        session.tick();
        if (owner.rippleTicks > 0) {
            owner.rippleTicks--;
        }
        if (owner.mode == Mode.EXERCISE) {
            InterfaceStepSpecs.Goal goal = owner.exerciseGoal;
            if (goal == null || !session.canInteract() || SessionOps.goalMet(session, goal) || --owner.exerciseTicksLeft <= 0) {
                endExercise();
            }
        }
        if (ui != null && owner.mode != Mode.WATCH && element.blocksTimeline()) {
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
        InterfaceOverlayElement element = current();
        if (element == null || element.scene() != scene || element.session().isDestroyed()) {
            return;
        }
        if (key != null) {
            target().scriptLog.removeIf(existing -> key.equals(existing.key()));
        }
        target().scriptLog.add(new ScriptOp(key, op));
        op.accept(element.session());
    }

    /** Drops earlier keyed ops whose key starts with {@code prefix} (replace-style slot steps). */
    public static void forgetScript(PonderScene scene, String prefix) {
        InterfaceOverlayElement element = current();
        if (element != null && element.scene() == scene) {
            target().scriptLog.removeIf(existing -> existing.key() != null && existing.key().startsWith(prefix));
        }
    }

    @Nullable
    public static SandboxSession sessionOf(PonderScene scene) {
        InterfaceOverlayElement element = current();
        return element == null || element.scene() != scene || element.session().isDestroyed() ? null : element.session();
    }

    /** Starts the ghost cursor for {@code action}; the instruction drives it with {@link #tickCursor}. */
    public static void startCursor(PonderScene scene, InterfaceStepSpecs.Action action) {
        if (sessionOf(scene) == null) {
            return;
        }
        target().cursorAction = action;
        target().cursorTick = 0;
        target().cursorPrevTick = 0;
    }

    public static void tickCursor(PonderScene scene, InterfaceStepSpecs.Action action, int tick) {
        if (target() == null || target().cursorAction != action) {
            return;
        }
        target().cursorPrevTick = target().cursorTick;
        target().cursorTick = tick;
    }

    public static void pressRipple() {
        if (target() != null) target().rippleTicks = 6;
    }

    public static void endCursor(PonderScene scene, InterfaceStepSpecs.Action action) {
        if (target() == null || target().cursorAction != action) {
            return;
        }
        SandboxSession session = sessionOf(scene);
        target().cursorRest = session == null ? null : cursorEnd(session, action);
        target().cursorAction = null;
    }

    /** Enters exercise mode for {@code await_interface}; skipped when the session cannot take input. */
    public static void beginExercise(PonderScene scene, InterfaceStepSpecs.Goal goal, @Nullable Component hint, int timeout) {
        SandboxSession session = sessionOf(scene);
        if (session == null || !session.canInteract() || SessionOps.goalMet(session, goal)) {
            return;
        }
        target().mode = Mode.EXERCISE;
        target().exerciseGoal = goal;
        target().exerciseHint = hint;
        target().exerciseTicksLeft = timeout <= 0 ? Integer.MAX_VALUE : timeout;
    }

    private static void endExercise() {
        // The player's changes are the point of an exercise, so they are kept.
        target().mode = Mode.WATCH;
        target().exerciseGoal = null;
        target().exerciseHint = null;
        resetInput();
    }

    // ---- Try mode -----------------------------------------------------------------------

    public static Mode mode() {
        return mode(target());
    }

    public static void enterTry() {
        InterfaceOverlayElement element = current();
        if (element != null && element.session().canInteract() && mode() == Mode.WATCH) {
            target().mode = Mode.TRY;
            target().cursorAction = null;
        }
    }

    public static void exitTry() {
        if (mode() == Mode.TRY) {
            resetToScript();
            target().mode = Mode.WATCH;
        }
    }

    /** Rebuilds the session from its descriptor and replays the scripted ops, discarding the player's changes. */
    public static void resetToScript() {
        InterfaceOverlayElement element = current();
        if (element == null) {
            return;
        }
        SandboxSession old = element.session();
        old.destroy("reset to script");
        SandboxSession fresh = SandboxSession.open(old.descriptor(), element.scene().getWorld());
        element.replaceSession(fresh);
        InterfaceSlotOverlayRenderer.replaceSession(old, fresh);
        resetInput();
        for (ScriptOp op : List.copyOf(target().scriptLog)) {
            op.apply().accept(fresh);
        }
    }

    // ---- Queries ------------------------------------------------------------------------

    private static boolean visible(Target owner, @Nullable Screen screen) {
        if (owner == null || owner.element == null || owner.element.session().isDestroyed()
            || !owner.element.isVisible()) return false;
        float partialTicks = previewViewport == null
            ? Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false) : previewViewport.partialTicks();
        if (!owner.element.hasVisibleContent(partialTicks)) return false;
        Screen host = previewViewport == null ? screen : previewViewport.screen();
        return host instanceof PonderUI ui && ui.getActiveScene() == owner.element.scene()
            && (!(ui instanceof PonderUiPreviewAccess access) || !access.ponderer$getFreePreview().isActive());
    }

    private static Target hitTarget(Screen screen, double x, double y) {
        for (int i = targets.size() - 1; i >= 0; i--) {
            Target owner = targets.get(i);
            if (!visible(owner, screen)) continue;
            int[] rect = owner.element.displayRect();
            if (rect != null && x >= rect[0] && y >= rect[1]
                && x < rect[0] + rect[2] && y < rect[1] + rect[3]) return owner;
            if (withTarget(owner, () -> tabs(owner.element.session()).stream().anyMatch(tab -> tab.contains(x, y))))
                return owner;
        }
        return null;
    }

    private static Target queryTarget(@Nullable Screen screen) {
        if (scopedTarget != null) return visible(scopedTarget, screen) ? scopedTarget : null;
        Minecraft mc = Minecraft.getInstance();
        Target hit = hitTarget(screen, mouseX(mc), mouseY(mc));
        if (hit != null) return hit;
        if (visible(focusedTarget, screen)) return focusedTarget;
        Target owner = target();
        return visible(owner, screen) ? owner : null;
    }

    public static List<SandboxSession> sessionsFor(Screen screen) {
        return targets.stream().filter(owner -> visible(owner, screen)).map(owner -> owner.element.session()).toList();
    }

    @Nullable public static SandboxSession sessionForNative(Screen screen) {
        for (Target owner : targets) if (owner.element.session().currentScreen() == screen) return owner.element.session();
        return null;
    }

    /** The session shown on {@code screen}'s active scene, if any. */
    @Nullable
    public static SandboxSession sessionFor(@Nullable Screen screen) {
        Target owner = queryTarget(screen);
        return owner == null ? null : owner.element.session();
    }

    @Nullable public static SandboxSession sessionAt(Screen screen, double x, double y) {
        Target owner = hitTarget(screen, x, y);
        return owner == null ? null : owner.element.session();
    }

    /** The embedded screen currently shown in PonderUI, used by UI anchors and JEI handlers. */
    @Nullable
    public static Screen activeScreen() {
        Target owner = target();
        return visible(owner, Minecraft.getInstance().screen) ? owner.element.session().currentScreen() : null;
    }

    /** GUI rectangle {left, top, width, height} of the interface on the current PonderUI, if any. */
    @Nullable
    public static int[] activeRect() {
        Target owner = queryTarget(Minecraft.getInstance().screen);
        return owner == null ? null : owner.element.displayRect();
    }

    @Nullable public static int[] screenRect(SandboxSession session) {
        Target owner = targetOf(session);
        return owner == null ? null : owner.element.displayRect();
    }

    /** Native GUI anchor bounds projected through the panel's crop and scale, without rounding. */
    @Nullable
    public static double[] activeAnchorRect() {
        return anchorRect(target());
    }

    @Nullable public static double[] anchorRect(Target owner) {
        if (owner == null || owner.element == null || owner.element.session().isDestroyed()) return null;
        InterfaceOverlayElement element = owner.element;
        SandboxSession session = element.session();
        int[] rect = session.guiRect();
        if (rect == null) return null;
        double[] start = element.toDisplay(rect[0], rect[1]);
        double[] end = element.toDisplay(rect[0] + rect[2], rect[1] + rect[3]);
        return new double[]{start[0], start[1], end[0] - start[0], end[1] - start[1]};
    }

    /** Maps a native menu rectangle to the visible panel (identity for a standalone interface). */
    public static int[] toScreenRect(int x, int y, int width, int height) {
        Target owner = queryTarget(Minecraft.getInstance().screen);
        return toScreenRect(owner == null ? null : owner.element.session(), x, y, width, height);
    }

    public static int[] toScreenRect(@Nullable SandboxSession session, int x, int y, int width, int height) {
        Target owner = targetOf(session);
        InterfaceOverlayElement element = owner == null ? null : owner.element;
        if (element == null) return new int[]{x, y, width, height};
        double[] start = element.toDisplay(x, y), end = element.toDisplay(x + width, y + height);
        int left = (int) Math.floor(start[0]), top = (int) Math.floor(start[1]);
        return new int[]{left, top, Math.max(1, (int) Math.ceil(end[0]) - left), Math.max(1, (int) Math.ceil(end[1]) - top)};
    }

    public static boolean isNativeRectVisible(int x, int y, int width, int height) {
        SandboxSession session = sessionFor(Minecraft.getInstance().screen);
        return isNativeRectVisible(session, x, y, width, height);
    }

    public static boolean isNativeRectVisible(SandboxSession session, int x, int y, int width, int height) {
        Target owner = targetOf(session);
        return owner == null || owner.element.isNativeRectVisible(x, y, width, height);
    }

    /** JEI targets must not expose slots covered by a later panel. */
    public static boolean isSlotExposed(SandboxSession session, int x, int y, int width, int height) {
        Target owner = targetOf(session);
        Screen screen = Minecraft.getInstance().screen;
        if (!visible(owner, screen) || !isNativeRectVisible(session, x, y, width, height)) return false;
        int[] rect = toScreenRect(session, x, y, width, height);
        for (int i = targets.indexOf(owner) + 1; i < targets.size(); i++) {
            Target otherOwner = targets.get(i);
            if (!visible(otherOwner, screen)) continue;
            InterfaceOverlayElement other = otherOwner.element;
            int[] cover = other.displayRect();
            if (cover != null && rect[0] < cover[0] + cover[2] && rect[0] + rect[2] > cover[0]
                && rect[1] < cover[1] + cover[3] && rect[1] + rect[3] > cover[1]) return false;
            if (withTarget(otherOwner, () -> tabs(other.session()).stream().anyMatch(tab ->
                rect[0] < tab.x() + tab.width() && rect[0] + rect[2] > tab.x()
                    && rect[1] < tab.y() + TAB_HEIGHT && rect[1] + rect[3] > tab.y()))) return false;
        }
        return true;
    }

    /** Whether real input currently goes to the sandbox (try or exercise mode). */
    public static boolean isTakingInput(@Nullable SandboxSession session) {
        Target owner = targetOf(session);
        return owner != null && owner.mode != Mode.WATCH && session.canInteract();
    }

    // ---- Input --------------------------------------------------------------------------

    public static boolean mouseClicked(Screen screen, double x, double y, int button) {
        Target owner = hitTarget(screen, x, y);
        if (owner == null) return false;
        focusedTarget = owner;
        return withTarget(owner, () -> mouseClickedHere(screen, x, y, button));
    }

    private static boolean mouseClickedHere(Screen screen, double x, double y, int button) {
        SandboxSession session = sessionFor(screen);
        if (session == null || PonderUiInteractionHelper.hasPriorityPonderButtonAt(screen, x, y)) {
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && clickTab(session, x, y)) {
            return true;
        }
        if (InterfaceExtensions.editing().isActive()) {
            if (!InterfaceExtensions.editing().ownsSession(session)) return false;
            if (current().clipsInputAt(x, y)) return false;
            double[] local = current().toNative(x, y);
            boolean handled = InterfaceExtensions.editing().pressed(session, local[0], local[1], button);
            if (handled) target().pressedButton = button;
            return handled;
        }
        if (!isInside(session, x, y) || !isTakingInput(session)) {
            return false;
        }
        double[] local = current().toNative(x, y);
        session.mouseClicked(local[0], local[1], button);
        target().pressedButton = button;
        target().lastDragX = x;
        target().lastDragY = y;
        return true;
    }

    public static boolean mouseReleased(Screen screen, double x, double y, int button) {
        Target owner = targets.stream().filter(t -> t.pressedButton == button).findFirst().orElse(focusedTarget);
        return owner != null && withTarget(owner, () -> mouseReleasedHere(screen, x, y, button));
    }

    private static boolean mouseReleasedHere(Screen screen, double x, double y, int button) {
        SandboxSession session = sessionFor(screen);
        if (session == null) {
            resetInput();
            return false;
        }
        if (InterfaceExtensions.editing().isActive()) {
            if (!InterfaceExtensions.editing().ownsSession(session)) return false;
            target().pressedButton = -1;
            double[] local = hitTarget(screen, x, y) != target() || current().clipsInputAt(x, y)
                ? new double[]{-1, -1} : current().toNative(x, y);
            return InterfaceExtensions.editing().released(session, local[0], local[1], button);
        }
        if (target().pressedButton != button) {
            return false;
        }
        target().pressedButton = -1;
        double[] local = hitTarget(screen, x, y) != target() || current().clipsInputAt(x, y)
            ? new double[]{-1, -1} : current().toNative(x, y);
        session.mouseReleased(local[0], local[1], button);
        return true;
    }

    public static boolean mouseScrolled(Screen screen, double x, double y, double scrollX, double scrollY) {
        Target owner = hitTarget(screen, x, y);
        return owner != null && withTarget(owner, () -> mouseScrolledHere(screen, x, y, scrollX, scrollY));
    }

    private static boolean mouseScrolledHere(Screen screen, double x, double y, double scrollX, double scrollY) {
        SandboxSession session = sessionFor(screen);
        if (session == null || !isInside(session, x, y) || InterfaceExtensions.editing().isActive()) {
            return false;
        }
        if (isTakingInput(session)) {
            double[] local = current().toNative(x, y);
            session.mouseScrolled(local[0], local[1], scrollX, scrollY);
        }
        // Scrolling over the interface must not switch Ponder scenes.
        return true;
    }

    public static boolean keyPressed(Screen screen, int key, int scanCode, int modifiers) {
        Target owner = visible(focusedTarget, screen) ? focusedTarget : queryTarget(screen);
        return owner != null && withTarget(owner, () -> keyPressedHere(screen, key, scanCode, modifiers));
    }

    private static boolean keyPressedHere(Screen screen, int key, int scanCode, int modifiers) {
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
            if (mode() == Mode.TRY) {
                exitTry();
                return true;
            }
            if (mode() == Mode.EXERCISE) {
                endExercise();
                return true;
            }
            return false;
        }
        if (!isTakingInput(session)) {
            return false;
        }
        return session.keyPressed(key, scanCode, modifiers);
    }

    public static boolean charTyped(Screen screen, char c, int modifiers) {
        Target owner = visible(focusedTarget, screen) ? focusedTarget : queryTarget(screen);
        return owner != null && withTarget(owner, () -> charTypedHere(screen, c, modifiers));
    }

    private static boolean charTypedHere(Screen screen, char c, int modifiers) {
        SandboxSession session = sessionFor(screen);
        if (!isTakingInput(session) || InterfaceExtensions.editing().isActive()) {
            return false;
        }
        return session.charTyped(c, modifiers);
    }

    /** Whether hover (tooltips, slot highlight) should follow the real cursor this frame. */
    static boolean shouldForwardHover(PonderUI ui, int mouseX, int mouseY) {
        return !PonderUiInteractionHelper.hasPriorityPonderButtonAt(ui, mouseX, mouseY)
            && hitTarget(ui, mouseX, mouseY) == target();
    }

    /**
     * Fabric has no screen drag event, so drags are synthesized for both loaders from cursor
     * movement while a forwarded button is held.
     */
    static void beforeRender(SandboxSession session, int mouseX, int mouseY) {
        InterfaceExtensions.editing().applyPreview(session);
        if (InterfaceExtensions.editing().isActive()) return;
        if (target().pressedButton < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double x = preciseMouseX(mc);
        double y = preciseMouseY(mc);
        if (x != target().lastDragX || y != target().lastDragY) {
            if (hitTarget(mc.screen, x, y) == target() && !current().clipsInputAt(x, y)) {
                double[] local = current().toNative(x, y);
                double[] previous = current().toNative(target().lastDragX, target().lastDragY);
                session.mouseDragged(local[0], local[1], target().pressedButton, local[0] - previous[0], local[1] - previous[1]);
            }
            target().lastDragX = x;
            target().lastDragY = y;
        }
    }

    private static boolean isInside(SandboxSession session, double x, double y) {
        int[] rect = activeRect();
        return rect != null && x >= rect[0] && y >= rect[1] && x < rect[0] + rect[2] && y < rect[1] + rect[3];
    }

    private static void resetInput() {
        Target owner = target();
        if (owner != null) owner.pressedButton = -1;
        if (owner != null && owner.element != null && InterfaceExtensions.editing().ownsSession(owner.element.session()))
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
        switch (mode()) {
            case WATCH -> {
                if (session.canInteract() && !current().isPanel()) {
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
        Component banner = switch (mode()) {
            case TRY -> Component.translatable("ponderer.ui.interface.try_notice").withStyle(ChatFormatting.GRAY);
            case EXERCISE -> target().exerciseHint == null
                ? Component.translatable("ponderer.ui.interface.exercise").withStyle(ChatFormatting.YELLOW)
                : target().exerciseHint.copy().withStyle(ChatFormatting.YELLOW);
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
        Target owner = targetOf(session);
        InterfaceStepSpecs.Action action = owner == null ? null : owner.cursorAction;
        if (action == null || owner.mode != Mode.WATCH || !action.cursor() || action.duration() <= 0) {
            return null;
        }
        double[] end = cursorEnd(session, action);
        double[] start = owner.cursorRest != null ? owner.cursorRest : center(session);
        if (end == null || start == null) {
            return null;
        }
        float t = Mth.clamp((Mth.lerp(partialTicks, owner.cursorPrevTick, owner.cursorTick)) / (float) action.duration(), 0, 1);
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
        if (p == null || !isNativeRectVisible(session, (int) p[0], (int) p[1], 1, 1)) {
            return;
        }
        Target owner = targetOf(session);
        p = owner.element.toDisplay(p[0], p[1]);
        int x = (int) p[0];
        int y = (int) p[1];
        if (owner.rippleTicks > 0) {
            int r = 2 + (6 - owner.rippleTicks) * 2;
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
