package com.nododiiiii.ponderer.forge;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.platform.services.RegistrationHelper;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/**
 * Forge implementation of RegistrationHelper using DeferredRegister.
 */
public class ForgeRegistrationHelper implements RegistrationHelper {

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Ponderer.MODID);

    @Override
    public Supplier<Item> registerItem(String id, Supplier<Item> itemSupplier) {
        RegistryObject<Item> obj = ITEMS.register(id, itemSupplier);
        return obj;
    }

    @Override
    public void init() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modEventBus);
    }
}
