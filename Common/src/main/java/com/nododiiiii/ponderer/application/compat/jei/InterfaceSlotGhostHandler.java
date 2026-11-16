package com.nododiiiii.ponderer.application.compat.jei;

import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.editor.capture.InterfaceSlotEditState;
import com.nododiiiii.ponderer.runtime.interfaces.InterfaceSlotOverlayRenderer;
import com.nododiiiii.ponderer.runtime.interfaces.host.InterfaceHost;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * Accepts JEI ghost ingredients for mirrored interface slots while the dedicated
 * slot-edit workflow is active.
 */
public class InterfaceSlotGhostHandler<T extends AbstractContainerScreen<?>> implements IGhostIngredientHandler<T> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(T gui, ITypedIngredient<I> ingredient, boolean doStart) {
        if (!InterfaceSlotEditState.isActive()) {
            return List.of();
        }

        JeiCompat.IngredientDescriptor descriptor = JeiCompat.resolveIngredientDescriptor(ingredient);
        if (descriptor == null || descriptor.id() == null || descriptor.id().isBlank()) {
            return List.of();
        }

        return createTargets(gui, descriptor, ingredient);
    }

    static <I> List<Target<I>> createTargets(AbstractContainerScreen<?> gui, JeiCompat.IngredientDescriptor descriptor,
                                            ITypedIngredient<I> ingredient) {
        var itemStack = ingredient.getItemStack();
        DslScene.InterfaceSlotBinding binding = itemStack.map(InterfaceSlotEditState::bindingFromStack).orElse(null);
        if (binding == null) {
            if (itemStack.isPresent()) {
                return List.of();
            }
            binding = new DslScene.InterfaceSlotBinding(0, descriptor.id(), descriptor.kind());
        }
        DslScene.InterfaceSlotBinding droppedBinding = binding;
        InterfaceSlotOverlayRenderer.ContainerBounds bounds = InterfaceSlotOverlayRenderer.readContainerBounds(gui);
        List<Target<I>> targets = new ArrayList<>();
        var session = InterfaceHost.sessionForNative(gui);
        if (session != null && !InterfaceSlotEditState.ownsSession(session)) return List.of();
        List<Slot> slots = gui.getMenu().slots;
        for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
            Slot slot = slots.get(slotIndex);
            if (!slot.isActive()) {
                continue;
            }
            Rect2i area = new Rect2i(bounds.left() + slot.x, bounds.top() + slot.y, 16, 16);
            if (session != null) {
                if (!InterfaceHost.isSlotExposed(
                    session, area.getX(), area.getY(), area.getWidth(), area.getHeight())) continue;
                int[] mapped = InterfaceHost.toScreenRect(
                    session, area.getX(), area.getY(), area.getWidth(), area.getHeight());
                area = new Rect2i(mapped[0], mapped[1], mapped[2], mapped[3]);
            }
            Rect2i targetArea = area;
            final int finalSlotIndex = slotIndex;
            targets.add(new Target<>() {
                @Override
                public Rect2i getArea() {
                    return targetArea;
                }

                @Override
                public void accept(I ignored) {
                    if (session != null && (session.isDestroyed() || !InterfaceSlotEditState.ownsSession(session))) return;
                    InterfaceSlotEditState.putBinding(finalSlotIndex, slot.x, slot.y, droppedBinding);
                }
            });
        }
        return targets;
    }

    @Override
    public boolean shouldHighlightTargets() {
        return false;
    }

    @Override
    public void onComplete() {
    }
}
