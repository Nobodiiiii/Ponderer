package com.nododiiiii.ponderer.neoforge.runtime;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod("pondererlib")
public final class PondererLibNeoForge {
    public PondererLibNeoForge(IEventBus bus) {
        if (FMLEnvironment.dist == Dist.CLIENT) PondererLibNeoForgeClient.install(bus);
    }
}
