package com.nododiiiii.ponderer.registry;

import com.nododiiiii.ponderer.blueprint.BlueprintFeature;
import com.nododiiiii.ponderer.platform.PondererServices;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

public final class ModCreativeModeTabs {

    public static final Supplier<CreativeModeTab> PONDERER = PondererServices.REGISTRATION.registerCreativeModeTab(
        "ponderer",
        Component.translatable("itemGroup.ponderer"),
        () -> new ItemStack(ModBlocks.MINIATURE_PROJECTOR_ITEM.get()),
        (parameters, output) -> {
            if (BlueprintFeature.shouldShowBlueprintInCreativeTab()) {
                output.accept(new ItemStack(ModItems.BLUEPRINT.get()));
            }
            output.accept(new ItemStack(ModBlocks.MINIATURE_PROJECTOR_ITEM.get()));
            output.accept(new ItemStack(ModBlocks.LIFE_SIZE_PROJECTOR_ITEM.get()));
        });

    private ModCreativeModeTabs() {
    }

    public static void init() {
    }
}
