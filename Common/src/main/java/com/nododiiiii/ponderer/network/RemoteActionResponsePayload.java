package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.ui.ProjectorConfigScreen;
import com.nododiiiii.ponderer.ui.RemoteBrowserScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RemoteActionResponsePayload(boolean success, String message, boolean refreshCatalog) implements CustomPacketPayload {

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
        buf.writeUtf(message() == null ? "" : message());
        buf.writeBoolean(refreshCatalog());
    }

    public static RemoteActionResponsePayload decode(RegistryFriendlyByteBuf buf) {
        return new RemoteActionResponsePayload(buf.readBoolean(), buf.readUtf(), buf.readBoolean());
    }

    public static void handle(RemoteActionResponsePayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof RemoteBrowserScreen screen) {
            screen.receiveAction(payload);
        } else if (client.screen instanceof ProjectorConfigScreen screen) {
            screen.receiveRemoteAction(payload);
        } else if (client.player != null && payload.message() != null && !payload.message().isBlank()) {
            client.player.displayClientMessage(net.minecraft.network.chat.Component.literal(payload.message()), false);
        }
    }
}
