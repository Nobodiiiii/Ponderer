package com.nododiiiii.ponderer.fabric;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.platform.services.RegistrationHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/**
 * Fabric implementation of RegistrationHelper using vanilla Registry.
 */
public class FabricRegistrationHelper implements RegistrationHelper {

    @Override
    public Supplier<Item> registerItem(String id, Supplier<Item> itemSupplier) {
        Item item = Registry.register(BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, id), itemSupplier.get());
        return () -> item;
    }

    @Override
    public void init() {
        // No-op on Fabric; items are registered eagerly on registerItem().
    }
}
