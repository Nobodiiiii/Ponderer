package com.nododiiiii.ponderer.interfaces.host;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.interfaces.model.InterfaceDescriptor;
import com.nododiiiii.ponderer.interfaces.sandbox.SandboxSession;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ui.InterfaceSlotEditState;
import com.nododiiiii.ponderer.ui.InterfaceSlotOverlayRenderer;
import com.nododiiiii.ponderer.ui.PonderRuntimeZLayers;
import com.nododiiiii.ponderer.ui.PonderUiInteractionHelper;
import com.nododiiiii.ponderer.ui.UiAnchorCoords;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

/**
 * Owns the (at most one) visible interface session and routes PonderUI input to it.
 * Loader modules call the input methods from their screen events; a {@code true} return
 * means the event was consumed and must not reach PonderUI.
 */
public final class InterfaceHost {

    @Nullable
    private static InterfaceOverlayElement current;

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

    /** Hides the interface shown by {@code scene}, if any (e.g. a later {@code show_structure}). */
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

    /** Client tick: ticks the visible session and drops it once the player is back in the world. */
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
        if (mc.screen instanceof PonderUI ui && ui.getActiveScene() == element.scene()) {
            element.session().tick();
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

    // ---- Input --------------------------------------------------------------------------

    public static boolean mouseClicked(Screen screen, double x, double y, int button) {
        SandboxSession session = sessionFor(screen);
        if (session == null || PonderUiInteractionHelper.hasPriorityPonderButtonAt(screen, x, y)) {
            return false;
        }
        if (InterfaceSlotEditState.isActive()) {
            return editPressed(session, x, y, button);
        }
        if (!isInside(session, x, y) || !session.canInteract()) {
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
        if (session.canInteract()) {
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
            return session.popScreen();
        }
        Minecraft mc = Minecraft.getInstance();
        if (!session.canInteract() || !isInside(session, mouseX(mc), mouseY(mc))) {
            return false;
        }
        return session.keyPressed(key, scanCode, modifiers);
    }

    public static boolean charTyped(Screen screen, char c, int modifiers) {
        SandboxSession session = sessionFor(screen);
        if (session == null || !session.canInteract() || InterfaceSlotEditState.isActive()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        return isInside(session, mouseX(mc), mouseY(mc)) && session.charTyped(c, modifiers);
    }

    /** Scripted click at a UI-anchor coordinate ({@code click_interface}). */
    public static void clickNormalized(double u, double v, int button) {
        SandboxSession session = sessionFor(Minecraft.getInstance().screen);
        if (session == null) {
            return;
        }
        int[] rect = session.guiRect();
        if (rect == null) {
            return;
        }
        double x = rect[0] + UiAnchorCoords.decodeToPixelX(u, Math.max(1, rect[2]));
        double y = rect[1] + UiAnchorCoords.decodeToPixelYTopLeft(v, Math.max(1, rect[3]));
        session.mouseClicked(x, y, button);
        session.mouseReleased(x, y, button);
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
        graphics.pose().translate(0, 0, PonderRuntimeZLayers.SLOT_ONLY_LAYER);
        RenderSystem.disableDepthTest();
        element.render(graphics, mouseX - 8, mouseY - 8);
        graphics.pose().popPose();
        graphics.flush();
    }
}
