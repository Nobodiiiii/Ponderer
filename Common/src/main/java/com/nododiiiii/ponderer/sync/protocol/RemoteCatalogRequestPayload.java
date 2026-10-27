package com.nododiiiii.ponderer.sync.protocol;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RemoteCatalogRequestPayload() implements CustomPacketPayload {

    public static final Type<RemoteCatalogRequestPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "remote_catalog_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteCatalogRequestPayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), RemoteCatalogRequestPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
    }

    public static RemoteCatalogRequestPayload decode(RegistryFriendlyByteBuf buf) {
        return new RemoteCatalogRequestPayload();
    }


}
