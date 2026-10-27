package com.nododiiiii.ponderer.registry;

import com.nododiiiii.ponderer.features.blueprint.BlueprintRegistration;
import com.nododiiiii.ponderer.features.projector.registry.ModBlocks;
import com.nododiiiii.ponderer.features.projector.registry.ModBlockEntities;
import com.nododiiiii.ponderer.features.projector.registry.ModMenuTypes;

/** Product registration order, preserving blueprint, blocks, creative tab, entities and menus. */
public final class ModItems {
    public static void init() {
        BlueprintRegistration.init();
        ModBlocks.init();
        ModCreativeModeTabs.init();
        ModBlockEntities.init();
        ModMenuTypes.init();
    }
    private ModItems() {}
}
