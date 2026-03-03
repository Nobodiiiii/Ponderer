package com.nododiiiii.ponderer.fabric;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.PondererClientCommands;
import com.nododiiiii.ponderer.registry.ModItems;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.neoforged.fml.config.ModConfig;

/**
 * Fabric main entrypoint.
 */
public class PondererFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        // Trigger static init of ModItems (registration via Fabric Registry)
        ModItems.init();

        // Register config via ForgeConfigAPIPort
        NeoForgeConfigRegistry.INSTANCE.register(Ponderer.MODID, ModConfig.Type.CLIENT, Config.SPEC);

        // Register network
        PondererServices.NETWORK.registerPackets();

        // Register client commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            PondererClientCommands.register(dispatcher);
        });
    }
}
