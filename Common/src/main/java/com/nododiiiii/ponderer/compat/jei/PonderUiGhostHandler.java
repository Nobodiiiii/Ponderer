package com.nododiiiii.ponderer.compat.jei;

import com.nododiiiii.ponderer.interfaces.host.InterfaceHost;
import com.nododiiiii.ponderer.interfaces.sandbox.SandboxSession;
import com.nododiiiii.ponderer.interfaces.sandbox.SessionOps;
import com.nododiiiii.ponderer.ui.InterfaceSlotEditState;
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
 * JEI drops onto the interface embedded in PonderUI: in slot-edit mode they become ghost
 * bindings; in try/exercise mode item drops put a real stack into the sandbox slot (only the
 * sandbox ever holds it).
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
            return InterfaceSlotGhostHandler.createTargets(container, descriptor);
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
    public void onComplete() {
    }
}
