package com.nododiiiii.ponderer.neoforge;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.platform.services.RegistrationHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * NeoForge implementation of RegistrationHelper using DeferredRegister.
 */
public class NeoForgeRegistrationHelper implements RegistrationHelper {

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, Ponderer.MODID);

    /** Set by PondererNeoForge before init() is called. */
    static IEventBus modEventBus;

    @Override
    public Supplier<Item> registerItem(String id, Supplier<Item> itemSupplier) {
        DeferredHolder<Item, Item> holder = ITEMS.register(id, itemSupplier);
        return holder;
    }

    @Override
    public void init() {
        ITEMS.register(modEventBus);
    }
}
