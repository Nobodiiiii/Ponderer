package com.nododiiiii.ponderer.platform.services;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import java.util.function.Supplier;

/**
 * Platform abstraction for item/block registration.
 * Forge: DeferredRegister + RegistryObject. Fabric: Registry.register().
 */
public interface RegistrationHelper {

    @FunctionalInterface
    interface MenuFactory<T extends AbstractContainerMenu> {
        T create(int windowId, Inventory playerInventory, BlockPos extraData);
    }

    /** Register an item. Returns a supplier that provides the registered item. */
    Supplier<Item> registerItem(String id, Supplier<Item> itemSupplier);

    /** Register a creative mode tab. Returns a supplier that provides the registered tab. */
    Supplier<CreativeModeTab> registerCreativeModeTab(String id, Component title, Supplier<ItemStack> iconSupplier,
                                                      CreativeModeTab.DisplayItemsGenerator displayItems);

    /** Register a block. Returns a supplier that provides the registered block. */
    <T extends Block> Supplier<T> registerBlock(String id, Supplier<T> blockSupplier);

    /** Register a block entity type. Returns a supplier that provides the registered type. */
    <T extends net.minecraft.world.level.block.entity.BlockEntity> Supplier<BlockEntityType<T>> registerBlockEntityType(
        String id, Supplier<BlockEntityType<T>> typeSupplier);

    /** Register an extended menu type. Returns a supplier that provides the registered type. */
    <T extends AbstractContainerMenu> Supplier<MenuType<T>> registerMenuType(String id, MenuFactory<T> factory);

    /** Perform post-registration setup (e.g., register the DeferredRegister to the mod bus on Forge). */
    void init();
}
