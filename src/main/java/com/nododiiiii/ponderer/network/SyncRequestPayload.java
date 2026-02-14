package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncRequestPayload() implements CustomPacketPayload {
    public static final Type<SyncRequestPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "sync_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncRequestPayload> CODEC =
        StreamCodec.of((buf, payload) -> {}, buf -> new SyncRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}