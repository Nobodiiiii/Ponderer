package com.nododiiiii.ponderer.neoforge.sticksnapshot;

import com.nododiiiii.ponderer.neoforge.sticksnapshot.client.ClientInputHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StickSnapshotFeature {
    public static final String MOD_ID = "sticksnapshot";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private StickSnapshotFeature() {
    }

    public static void onClientInit() {
        ClientInputHandler.register();
    }
}
