package com.nododiiiii.ponderer.fabric;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.registry.ModItems;
import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
import net.fabricmc.api.ModInitializer;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Fabric main entrypoint.
 */
public class PondererFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        // Trigger static init of ModItems (registration via Fabric Registry)
        ModItems.init();

        // Register config via ForgeConfigAPIPort
        ForgeConfigRegistry.INSTANCE.register(Ponderer.MODID, ModConfig.Type.CLIENT, Config.SPEC);

        // Register network
        PondererServices.NETWORK.registerPackets();
    }
}
