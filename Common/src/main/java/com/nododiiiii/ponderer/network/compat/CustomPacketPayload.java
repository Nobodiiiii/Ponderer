package com.nododiiiii.ponderer.network.compat;

import net.minecraft.resources.ResourceLocation;

/** 1.20.1 transport identity for the shared payloads. */
public interface CustomPacketPayload {
    record Type<T extends CustomPacketPayload>(ResourceLocation id) {}
    Type<? extends CustomPacketPayload> type();
}
