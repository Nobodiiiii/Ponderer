package com.nododiiiii.ponderer.compat.jei;

import com.nododiiiii.ponderer.runtime.spi.IngredientResolver;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.world.level.material.Fluid;

/** Optional ingredient rendering capability for playback and interface overlays. */
public final class RuntimeJeiIngredients implements IngredientResolver {
    @Override public boolean isAvailable() { return JeiCompat.isAvailable(); }
    @Override public ScreenElement fluid(Fluid fluid, int amount) { return JeiCompat.createFluidIngredientElement(fluid, amount); }
    @Override public ScreenElement resolve(String id) { return JeiCompat.resolveIngredientById(id); }
    @Override public ScreenElement resolve(String id, String kind) { return JeiCompat.resolveIngredientById(id, kind); }
}
