package com.nododiiiii.ponderer.registry;

import com.nododiiiii.ponderer.blueprint.BlueprintFeature;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.projector.ProjectorFeature;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

public final class ModCreativeModeTabs {

    public static final Supplier<CreativeModeTab> PONDERER = PondererServices.REGISTRATION.registerCreativeModeTab(
        "ponderer",
        Component.translatable("itemGroup.ponderer"),
        ModCreativeModeTabs::icon,
        (parameters, output) -> {
            if (BlueprintFeature.shouldShowBlueprintInCreativeTab()) {
                output.accept(new ItemStack(ModItems.BLUEPRINT.get()));
            }
            if (ProjectorFeature.shouldShowProjectorsInCreativeTab()) {
                output.accept(new ItemStack(ModBlocks.MINIATURE_PROJECTOR_ITEM.get()));
                output.accept(new ItemStack(ModBlocks.LIFE_SIZE_PROJECTOR_ITEM.get()));
            }
        });

    private ModCreativeModeTabs() {
    }

    public static void init() {
    }

    private static ItemStack icon() {
        if (ProjectorFeature.shouldShowProjectorsInCreativeTab()) {
            return new ItemStack(ModBlocks.LIFE_SIZE_PROJECTOR_ITEM.get());
        }
        if (BlueprintFeature.shouldShowBlueprintInCreativeTab()) {
            return new ItemStack(ModItems.BLUEPRINT.get());
        }
        return new ItemStack(ModBlocks.LIFE_SIZE_PROJECTOR_ITEM.get());
    }
}
