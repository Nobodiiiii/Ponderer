package com.nododiiiii.ponderer.runtime.spi;

import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.world.level.material.Fluid;
import javax.annotation.Nullable;

/** Optional rendering capability; implementations own third-party API linkage. */
public interface IngredientResolver {
    default boolean isAvailable() { return false; }
    @Nullable default ScreenElement fluid(Fluid fluid, int amount) { return null; }
    @Nullable default ScreenElement resolve(String id) { return null; }
    @Nullable default ScreenElement resolve(String id, String kind) { return resolve(id); }
    IngredientResolver NONE = new IngredientResolver() {};
}
