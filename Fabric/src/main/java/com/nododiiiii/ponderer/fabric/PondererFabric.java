package com.nododiiiii.ponderer.fabric;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.registry.ModItems;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import net.fabricmc.api.ModInitializer;
import net.neoforged.fml.config.ModConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Fabric main entrypoint.
 */
public class PondererFabric implements ModInitializer {

    private static final ResourceLocation REQUIRED_CLIENT_CHANNEL =
        ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "sync_response");

    @Override
    public void onInitialize() {
        // Trigger static init of ModItems (registration via Fabric Registry)
        ModItems.init();

        // Register config via ForgeConfigAPIPort
        NeoForgeConfigRegistry.INSTANCE.register(Ponderer.MODID, ModConfig.Type.CLIENT, Config.SPEC);

        // Register network
        PondererServices.NETWORK.registerPackets();

        // If server has this mod, connecting clients must expose our clientbound channel.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!ServerPlayNetworking.canSend(handler.player, REQUIRED_CLIENT_CHANNEL)) {
                handler.disconnect(Component.literal("Ponderer is required on client when installed on this server."));
            }
        });
    }
}
