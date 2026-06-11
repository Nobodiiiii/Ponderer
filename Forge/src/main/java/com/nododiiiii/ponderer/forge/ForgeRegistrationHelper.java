package com.nododiiiii.ponderer.forge;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.platform.services.RegistrationHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
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
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Ponderer.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Ponderer.MODID);
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, Ponderer.MODID);

    @Override
    public Supplier<Item> registerItem(String id, Supplier<Item> itemSupplier) {
        RegistryObject<Item> obj = ITEMS.register(id, itemSupplier);
        return obj;
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
        return MENU_TYPES.register(id, () -> IForgeMenuType.create(factory::create));
    }

    @Override
    public void init() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modEventBus);
        BLOCKS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
    }
}
