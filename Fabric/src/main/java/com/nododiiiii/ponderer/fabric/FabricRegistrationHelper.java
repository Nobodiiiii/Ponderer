package com.nododiiiii.ponderer.fabric;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.platform.services.RegistrationHelper;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

    private static final StreamCodec<RegistryFriendlyByteBuf, BlockPos> BLOCK_POS_STREAM_CODEC =
        StreamCodec.of((buf, pos) -> buf.writeBlockPos(pos), buf -> buf.readBlockPos());

    @Override
    public Supplier<Item> registerItem(String id, Supplier<Item> itemSupplier) {
        Item item = Registry.register(BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, id), itemSupplier.get());
        return () -> item;
    }

    @Override
    public Supplier<CreativeModeTab> registerCreativeModeTab(String id, Component title, Supplier<ItemStack> iconSupplier,
                                                             CreativeModeTab.DisplayItemsGenerator displayItems) {
        CreativeModeTab tab = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
                ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, id), FabricItemGroup.builder()
                    .title(title)
                    .icon(iconSupplier)
                    .displayItems(displayItems)
                    .build());
        return () -> tab;
    }

    @Override
    public <T extends Block> Supplier<T> registerBlock(String id, Supplier<T> blockSupplier) {
        T block = Registry.register(BuiltInRegistries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, id), blockSupplier.get());
        return () -> block;
    }

    @Override
    public <T extends BlockEntity> Supplier<BlockEntityType<T>> registerBlockEntityType(
        String id, Supplier<BlockEntityType<T>> typeSupplier) {
        BlockEntityType<T> type = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, id), typeSupplier.get());
        return () -> type;
    }

    @Override
    public <T extends AbstractContainerMenu> Supplier<MenuType<T>> registerMenuType(
        String id, MenuFactory<T> factory) {
        MenuType<T> type = Registry.register(BuiltInRegistries.MENU,
                ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, id),
                new ExtendedScreenHandlerType<>(factory::create, BLOCK_POS_STREAM_CODEC));
        return () -> type;
    }

    @Override
    public void init() {
        // No-op on Fabric; items are registered eagerly on registerItem().
    }
}
