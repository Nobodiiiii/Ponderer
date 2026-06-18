package com.nododiiiii.ponderer.forge;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.FeatureAvailability;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.forge.sticksnapshot.StickSnapshotFeature;
import com.nododiiiii.ponderer.network.FeatureAvailabilityPayload;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.registry.ModItems;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

@Mod(Ponderer.MODID)
public class PondererForge {

    /** Orphaned pack names detected during startup. */
    static List<String> pendingOrphanedPacks = new ArrayList<>();
    /** Pack updates detected during startup. */
    static List<SceneStore.PackUpdateInfo> pendingPackUpdates = new ArrayList<>();

    public PondererForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Force static init of ModItems FIRST so all items are added to DeferredRegister
        // BEFORE init() registers the DeferredRegister to the mod event bus.
        // If ModItems class loads after RegisterEvent fires, Forge throws IllegalStateException.
        ModItems.init();
        // Now register DeferredRegister to event bus
        PondererServices.REGISTRATION.init();

        // Config
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SERVER_SPEC);

        modEventBus.addListener(this::onCommonSetup);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            // All client event registration is in a separate class to avoid
            // loading client-only classes on the dedicated server.
            PondererForgeClient.init(modEventBus);
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        PondererServices.NETWORK.registerPackets();
        StickSnapshotFeature.onCommonSetup(event);
    }

    private void onServerStarted(ServerStartedEvent event) {
        FeatureAvailability.captureFromConfig();
    }

    private void onServerStopping(ServerStoppingEvent event) {
        FeatureAvailability.reset();
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FeatureAvailabilityPayload.sendTo(player);
        }
    }
}
