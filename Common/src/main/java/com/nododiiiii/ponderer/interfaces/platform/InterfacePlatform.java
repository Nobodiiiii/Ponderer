package com.nododiiiii.ponderer.interfaces.platform;

import com.nododiiiii.ponderer.platform.PondererServices;

/**
 * Lazy holder so the client-only platform service is resolved on first use.
 */
public final class InterfacePlatform {

    private InterfacePlatform() {
    }

    public static InterfaceSandboxPlatform get() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final InterfaceSandboxPlatform INSTANCE = PondererServices.load(InterfaceSandboxPlatform.class);
    }
}
