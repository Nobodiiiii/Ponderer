package com.nododiiiii.ponderer.ui;

import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Interface for screens that support JEI item browsing integration.
 */
public interface JeiAwareScreen {

    record IngredientTarget(HintableTextFieldWidget field, IdFieldMode mode, Rect2i area) {}

    /** Returns the visible ingredient slots, each with its own field and ID type. */
    List<IngredientTarget> getJeiIngredientTargets();

    default void onJeiItemDropped(HintableTextFieldWidget field, ItemStack stack) {}

    /** Public getters for JEI compat layer (IGuiProperties). */
    int getGuiLeft();
    int getGuiTop();
    int getGuiWidth();
    int getGuiHeight();
}
