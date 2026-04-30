package com.nododiiiii.ponderer.registry;

import com.nododiiiii.ponderer.blueprint.BlueprintItem;
import com.nododiiiii.ponderer.platform.PondererServices;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/**
 * Platform-agnostic item registration.
 * The actual registration mechanism is handled by RegistrationHelper SPI.
 */
public class ModItems {
    public static final Supplier<Item> BLUEPRINT = PondererServices.REGISTRATION.registerItem("blueprint",
        () -> new BlueprintItem(new Item.Properties().stacksTo(1)));

    /** Call from platform init to ensure static fields are loaded. */
    public static void init() {}

    private ModItems() {}
}
