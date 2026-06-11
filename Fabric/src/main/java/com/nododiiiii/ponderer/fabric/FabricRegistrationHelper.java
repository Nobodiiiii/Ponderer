package com.nododiiiii.ponderer.fabric;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.platform.services.RegistrationHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;

import java.util.function.Supplier;

/**
 * Fabric implementation of RegistrationHelper using vanilla Registry.
 */
public class FabricRegistrationHelper implements RegistrationHelper {

    @Override
    public Supplier<Item> registerItem(String id, Supplier<Item> itemSupplier) {
        Item item = Registry.register(BuiltInRegistries.ITEM,
                new ResourceLocation(Ponderer.MODID, id), itemSupplier.get());
        return () -> item;
    }

    @Override
    public <T extends Block> Supplier<T> registerBlock(String id, Supplier<T> blockSupplier) {
        T block = Registry.register(BuiltInRegistries.BLOCK,
                new ResourceLocation(Ponderer.MODID, id), blockSupplier.get());
        return () -> block;
    }

    @Override
    public <T extends BlockEntity> Supplier<BlockEntityType<T>> registerBlockEntityType(
        String id, Supplier<BlockEntityType<T>> typeSupplier) {
        BlockEntityType<T> type = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                new ResourceLocation(Ponderer.MODID, id), typeSupplier.get());
        return () -> type;
    }

    @Override
    public <T extends AbstractContainerMenu> Supplier<MenuType<T>> registerMenuType(
        String id, MenuFactory<T> factory) {
        MenuType<T> type = Registry.register(BuiltInRegistries.MENU,
                new ResourceLocation(Ponderer.MODID, id), new ExtendedScreenHandlerType<>(factory::create));
        return () -> type;
    }

    @Override
    public void init() {
        // No-op on Fabric; items are registered eagerly on registerItem().
    }
}
