package com.nododiiiii.ponderer.editor.capture;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.runtime.interfaces.sandbox.SandboxSession;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.editor.capture.InterfaceSlotEditState;
import com.nododiiiii.ponderer.runtime.interfaces.InterfaceSlotOverlayRenderer;
import com.nododiiiii.ponderer.runtime.viewer.PonderRuntimeZLayers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import com.nododiiiii.ponderer.runtime.spi.InterfaceEditingExtension;

public final class SlotDragController implements InterfaceEditingExtension {
    private static DslScene.InterfaceSlotBinding draggedBinding;
    private static Integer draggedBindingOrigin;
    private static ItemStack draggedItemStack = ItemStack.EMPTY;
    @Override public boolean isActive() { return InterfaceSlotEditState.isActive(); }
    @Override public void applyPreview(SandboxSession session) { InterfaceSlotEditState.applyPreview(session); }
    @Override public java.util.Map<Integer, DslScene.InterfaceSlotBinding> ghostBindings() { return InterfaceSlotEditState.getGhostBindingsForRender(); }
    @Override public void resetInput() { draggedBinding = null; draggedBindingOrigin = null; draggedItemStack = ItemStack.EMPTY; }
    private static boolean isInside(SandboxSession session, double x, double y) {
        int[] rect = session.guiRect();
        return rect != null && x >= rect[0] && y >= rect[1] && x < rect[0] + rect[2] && y < rect[1] + rect[3];
    }

    @Override public boolean pressed(SandboxSession session, double x, double y, int button) {
        InterfaceSlotEditState.applyPreview(session);
        Screen screen = session.currentScreen();
        Slot slot = screen == null ? null : InterfaceSlotOverlayRenderer.findSlotAt(screen, x, y);
        if (slot == null) {
            return isInside(session, x, y);
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            cancelDrag();
            if (InterfaceSlotEditState.getBindingForSlot(slot) != null) {
                InterfaceSlotEditState.clearBinding(slot);
            }
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            cancelDrag();
            DslScene.InterfaceSlotBinding binding = InterfaceSlotEditState.getBindingForSlot(slot);
            if (binding != null) {
                binding.slotIndex = slot.index;
                binding.slotX = slot.x;
                binding.slotY = slot.y;
                draggedBinding = binding;
                draggedBindingOrigin = binding.slotIndex;
                draggedItemStack = "item".equalsIgnoreCase(binding.ingredientKind) ? slot.getItem().copy() : ItemStack.EMPTY;
                InterfaceSlotEditState.clearBinding(slot);
            }
        }
        return true;
    }

    @Override public boolean released(SandboxSession session, double x, double y, int button) {
        Screen screen = session.currentScreen();
        Slot slot = screen == null ? null : InterfaceSlotOverlayRenderer.findSlotAt(screen, x, y);
        DslScene.InterfaceSlotBinding dragged = draggedBinding;
        if (dragged != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (slot != null) {
                InterfaceSlotEditState.putBinding(slot.index, slot.x, slot.y, dragged);
            } else if (draggedBindingOrigin != null) {
                InterfaceSlotEditState.putBinding(draggedBindingOrigin, dragged.slotX, dragged.slotY, dragged);
            }
            draggedBinding = null;
            draggedBindingOrigin = null;
            draggedItemStack = ItemStack.EMPTY;
            return true;
        }
        // Only consume drags started by this editor. JEI needs the release event
        // over a target slot to accept ingredients dragged from its overlay.
        return false;
    }

    /** Finishing an edit or cancelling a drag keeps the ingredient in its original slot. */
    @Override public void cancelDrag() {
        DslScene.InterfaceSlotBinding dragged = draggedBinding;
        Integer origin = draggedBindingOrigin;
        draggedBinding = null;
        draggedBindingOrigin = null;
        draggedItemStack = ItemStack.EMPTY;
        if (dragged != null && origin != null && InterfaceSlotEditState.isActive()) {
            InterfaceSlotEditState.putBinding(origin, dragged.slotX, dragged.slotY, dragged);
        }
    }

    @Override public void renderDragged(GuiGraphics graphics, int mouseX, int mouseY) {
        DslScene.InterfaceSlotBinding dragged = draggedBinding;
        if (!InterfaceSlotEditState.isActive() || dragged == null) {
            return;
        }
        var element = draggedItemStack.isEmpty()
            ? JeiCompat.resolveIngredientById(dragged.ingredientId, dragged.ingredientKind) : null;
        if (element == null && draggedItemStack.isEmpty()) {
            return;
        }
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, PonderRuntimeZLayers.INTERFACE_OVERLAY_LAYER);
        RenderSystem.disableDepthTest();
        if (!draggedItemStack.isEmpty()) {
            graphics.renderItem(draggedItemStack, mouseX - 8, mouseY - 8);
            graphics.renderItemDecorations(Minecraft.getInstance().font, draggedItemStack, mouseX - 8, mouseY - 8);
        } else {
            element.render(graphics, mouseX - 8, mouseY - 8);
        }
        graphics.pose().popPose();
        graphics.flush();
    }
    @Override public void renderHint(GuiGraphics graphics, Screen screen, int mouseX, int mouseY) {
        if (!InterfaceSlotEditState.isActive() || InterfaceSlotOverlayRenderer.findSlotAt(screen, mouseX, mouseY) == null) {
            return;
        }
        String hint = JeiCompat.getIngredientListVisibilityHint();
        if (hint == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        graphics.flush();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0, 0, PonderRuntimeZLayers.TOOLTIP_LAYER);
            RenderSystem.disableDepthTest();
            graphics.renderTooltip(mc.font, mc.font.split(Component.literal(hint), 240), mouseX, mouseY);
        } finally {
            graphics.pose().popPose();
            graphics.flush();
        }
    }
}
