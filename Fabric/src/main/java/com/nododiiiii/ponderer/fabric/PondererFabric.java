package com.nododiiiii.ponderer.fabric;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.FeatureAvailability;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.network.FeatureAvailabilityPayload;
import com.nododiiiii.ponderer.network.SyncResponsePayload;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.registry.ModItems;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import net.fabricmc.api.ModInitializer;
import net.neoforged.fml.config.ModConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;

/**
 * Fabric main entrypoint.
 */
public class PondererFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        // Trigger static init of ModItems (registration via Fabric Registry)
        ModItems.init();
        FabricRecipeConditions.register();

        // Register config via ForgeConfigAPIPort
        NeoForgeConfigRegistry.INSTANCE.register(Ponderer.MODID, ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
        NeoForgeConfigRegistry.INSTANCE.register(Ponderer.MODID, ModConfig.Type.SERVER, Config.SERVER_SPEC);

        // Register network
        PondererServices.NETWORK.registerPackets();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> FeatureAvailability.captureFromConfig());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> FeatureAvailability.reset());

        // If server has this mod, connecting clients must expose our clientbound channel.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!ServerPlayNetworking.canSend(handler, SyncResponsePayload.TYPE)
                || !ServerPlayNetworking.canSend(handler, FeatureAvailabilityPayload.TYPE)) {
                handler.disconnect(Component.literal("Ponderer is required on client when installed on this server."));
                return;
            }
            FeatureAvailabilityPayload.sendTo(handler.player);
        });
    }
}
