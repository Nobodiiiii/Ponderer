package com.nododiiiii.ponderer.compat.jei;

import com.nododiiiii.ponderer.runtime.interfaces.host.InterfaceHost;
import com.nododiiiii.ponderer.runtime.interfaces.sandbox.SandboxSession;
import com.nododiiiii.ponderer.runtime.interfaces.sandbox.SessionOps;
import com.nododiiiii.ponderer.editor.capture.InterfaceSlotEditState;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JEI drops onto the interface embedded in PonderUI: slot edits persist bindings and write
 * items into the sandbox menu; try/exercise drops place a real stack through normal slot
 * rules. Non-item ingredients remain display-only ghosts.
 */
public class PonderUiGhostHandler implements IGhostIngredientHandler<PonderUI> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(PonderUI gui, ITypedIngredient<I> ingredient, boolean doStart) {
        SandboxSession session = InterfaceHost.sessionFor(gui);
        Screen screen = session == null ? null : session.currentScreen();
        if (!(screen instanceof AbstractContainerScreen<?> container)) {
            return List.of();
        }

        if (InterfaceSlotEditState.isActive()) {
            JeiCompat.IngredientDescriptor descriptor = JeiCompat.resolveIngredientDescriptor(ingredient);
            if (descriptor == null || descriptor.id() == null || descriptor.id().isBlank()) {
                return List.of();
            }
            return InterfaceSlotGhostHandler.createTargets(container, descriptor, ingredient);
        }

        Optional<ItemStack> stack = ingredient.getItemStack();
        if (!InterfaceHost.isTakingInput(session) || stack.isEmpty() || stack.get().isEmpty()) {
            return List.of();
        }
        int[] rect = session.guiRect();
        if (rect == null) {
            return List.of();
        }
        ItemStack dropped = stack.get().copy();
        List<Target<I>> targets = new ArrayList<>();
        List<Slot> slots = container.getMenu().slots;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (!slot.isActive() || !slot.mayPlace(dropped)) {
                continue;
            }
            Rect2i area = new Rect2i(rect[0] + slot.x, rect[1] + slot.y, 16, 16);
            int index = i;
            targets.add(new Target<>() {
                @Override
                public Rect2i getArea() {
                    return area;
                }

                @Override
                public void accept(I ignored) {
                    SessionOps.placeStack(session, index, dropped);
                }
            });
        }
        return targets;
    }

    @Override
    public boolean shouldHighlightTargets() {
        return !InterfaceSlotEditState.isActive();
    }

    @Override
    public void onComplete() {
    }
}
