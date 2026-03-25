package com.nododiiiii.ponderer.forge.sticksnapshot;

import com.nododiiiii.ponderer.forge.sticksnapshot.client.ClientInputHandler;
import com.nododiiiii.ponderer.forge.sticksnapshot.network.ModNetworking;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StickSnapshotFeature {
    public static final String MOD_ID = "sticksnapshot";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private StickSnapshotFeature() {
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetworking::register);
    }

    public static void onClientInit() {
        ClientInputHandler.register();
    }
}
