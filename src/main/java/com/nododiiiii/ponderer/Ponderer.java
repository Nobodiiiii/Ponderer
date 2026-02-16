package com.nododiiiii.ponderer;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.ModLoadingContext;
import com.nododiiiii.ponderer.blueprint.BlueprintFeature;
import com.nododiiiii.ponderer.ponder.DynamicPonderPlugin;
import com.nododiiiii.ponderer.ponder.PondererClientCommands;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.registry.ModItems;
import com.nododiiiii.ponderer.network.PondererNetwork;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.enums.PonderConfig;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

@Mod(Ponderer.MODID)
public class Ponderer {
    public static final String MODID = "ponderer";

    public Ponderer() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modEventBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);

        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::onBuildCreativeTab);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        PondererNetwork.register();
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            PonderConfig.Client().editingMode.set(true);
            SceneStore.extractDefaultsIfNeeded();
            SceneStore.reloadFromDisk();
            PonderIndex.addPlugin(new DynamicPonderPlugin());
            PonderIndex.reload();
        });
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        PondererClientCommands.register(event);
    }

    private void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            if (BlueprintFeature.shouldShowBlueprintInCreativeTab()) {
                event.accept(new ItemStack(ModItems.BLUEPRINT.get()));
            }
        }
    }
}
