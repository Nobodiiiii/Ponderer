package com.nododiiiii.ponderer.registry;

import com.nododiiiii.ponderer.blueprint.BlueprintItem;
import com.nododiiiii.ponderer.platform.PondererServices;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/**
 * Mod items registered via platform-agnostic RegistrationHelper.
 */
public class ModItems {

    public static final Supplier<Item> BLUEPRINT = PondererServices.REGISTRATION.registerItem("blueprint",
        () -> new BlueprintItem(new Item.Properties().stacksTo(1)));

    /** Force static init so registration runs. */
    public static void init() {}

    private ModItems() {}
}
