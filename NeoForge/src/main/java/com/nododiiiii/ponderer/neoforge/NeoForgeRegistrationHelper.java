package com.nododiiiii.ponderer.neoforge;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.platform.services.RegistrationHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * NeoForge implementation of RegistrationHelper using DeferredRegister.
 */
public class NeoForgeRegistrationHelper implements RegistrationHelper {

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, Ponderer.MODID);
    private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Ponderer.MODID);
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(BuiltInRegistries.BLOCK, Ponderer.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Ponderer.MODID);
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(BuiltInRegistries.MENU, Ponderer.MODID);

    /** Set by PondererNeoForge before init() is called. */
    static IEventBus modEventBus;

    @Override
    public Supplier<Item> registerItem(String id, Supplier<Item> itemSupplier) {
        DeferredHolder<Item, Item> holder = ITEMS.register(id, itemSupplier);
        return holder;
    }

    @Override
    public Supplier<CreativeModeTab> registerCreativeModeTab(String id, Component title, Supplier<ItemStack> iconSupplier,
                                                             CreativeModeTab.DisplayItemsGenerator displayItems) {
        return CREATIVE_MODE_TABS.register(id, () -> CreativeModeTab.builder()
            .title(title)
            .icon(iconSupplier)
            .displayItems(displayItems)
            .build());
    }

    @Override
    public <T extends Block> Supplier<T> registerBlock(String id, Supplier<T> blockSupplier) {
        return BLOCKS.register(id, blockSupplier);
    }

    @Override
    public <T extends BlockEntity> Supplier<BlockEntityType<T>> registerBlockEntityType(
        String id, Supplier<BlockEntityType<T>> typeSupplier) {
        return BLOCK_ENTITY_TYPES.register(id, typeSupplier);
    }

    @Override
    public <T extends AbstractContainerMenu> Supplier<MenuType<T>> registerMenuType(
        String id, MenuFactory<T> factory) {
        return MENU_TYPES.register(id, () -> IMenuTypeExtension.create(
            (windowId, inventory, extraData) -> factory.create(windowId, inventory, extraData.readBlockPos())));
    }

    @Override
    public void init() {
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        BLOCKS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
    }
}
