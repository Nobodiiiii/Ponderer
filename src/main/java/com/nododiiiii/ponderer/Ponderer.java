package com.nododiiiii.ponderer;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import com.nododiiiii.ponderer.blueprint.BlueprintFeature;
import com.nododiiiii.ponderer.ponder.DynamicPonderPlugin;
import com.nododiiiii.ponderer.ponder.PondererClientCommands;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.registry.ModItems;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.enums.PonderConfig;
import com.nododiiiii.ponderer.network.PondererNetwork;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(Ponderer.MODID)
public class Ponderer {
    public static final String MODID = "ponderer";

    public Ponderer(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.ITEMS.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);

        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::onRegisterPayloads);
        modEventBus.addListener(this::onBuildCreativeTab);
        modEventBus.addListener(ModKeyBindings::register);
        NeoForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
        NeoForge.EVENT_BUS.addListener(ModKeyEvents::onClientTick);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            PonderConfig.client().editingMode.set(true);
            SceneStore.extractDefaultsIfNeeded();
            SceneStore.reloadFromDisk();
            PonderIndex.addPlugin(new DynamicPonderPlugin());
            PonderIndex.reload();
            PonderConfig.client().editingMode.set(false);
        });
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        PondererClientCommands.register(event);
    }

    private void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PondererNetwork.register(event);
    }

    private void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            if (BlueprintFeature.shouldShowBlueprintInCreativeTab()) {
                event.accept(new ItemStack(ModItems.BLUEPRINT.get()));
            }
        }
    }
}
