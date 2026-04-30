package com.nododiiiii.ponderer.compat.jei;

import com.nododiiiii.ponderer.ui.InterfaceSlotEditState;
import com.nododiiiii.ponderer.ui.UiAnchorViewport;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.List;

/**
 * Lets JEI target the embedded mirror slots while PonderUI is the active screen.
 */
public class PonderUiGhostHandler implements IGhostIngredientHandler<PonderUI> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(PonderUI gui, ITypedIngredient<I> ingredient, boolean doStart) {
        if (!InterfaceSlotEditState.isActive()) {
            return List.of();
        }

        Screen mirror = UiAnchorViewport.getEmbeddedMirrorScreen();
        if (!(mirror instanceof AbstractContainerScreen<?> container)) {
            return List.of();
        }

        JeiCompat.IngredientDescriptor descriptor = JeiCompat.resolveIngredientDescriptor(ingredient);
        if (descriptor == null || descriptor.id() == null || descriptor.id().isBlank()) {
            return List.of();
        }

        return InterfaceSlotGhostHandler.createTargets(container, descriptor);
    }

    @Override
    public void onComplete() {
    }
}
