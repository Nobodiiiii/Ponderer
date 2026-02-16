package com.nododiiiii.ponderer.network;

public record SyncRequestPayload() {
    public void encode(net.minecraft.network.FriendlyByteBuf buf) {
    }

    public static SyncRequestPayload decode(net.minecraft.network.FriendlyByteBuf buf) {
        return new SyncRequestPayload();
    }
}
