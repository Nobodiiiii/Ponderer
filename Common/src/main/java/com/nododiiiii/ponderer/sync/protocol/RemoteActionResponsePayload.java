package com.nododiiiii.ponderer.sync.protocol;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RemoteActionResponsePayload(boolean success, net.minecraft.network.chat.Component message, boolean refreshCatalog) implements CustomPacketPayload {

    public static final Type<RemoteActionResponsePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "remote_action_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteActionResponsePayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), RemoteActionResponsePayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(success());
        net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(buf, message());
        buf.writeBoolean(refreshCatalog());
    }

    public static RemoteActionResponsePayload decode(RegistryFriendlyByteBuf buf) {
        return new RemoteActionResponsePayload(buf.readBoolean(), net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(buf), buf.readBoolean());
    }


}
