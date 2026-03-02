package com.nododiiiii.ponderer.platform.services;

import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/**
 * Platform abstraction for item/block registration.
 * Forge: DeferredRegister + RegistryObject. Fabric: Registry.register().
 */
public interface RegistrationHelper {

    /** Register an item. Returns a supplier that provides the registered item. */
    Supplier<Item> registerItem(String id, Supplier<Item> itemSupplier);

    /** Perform post-registration setup (e.g., register the DeferredRegister to the mod bus on Forge). */
    void init();
}
