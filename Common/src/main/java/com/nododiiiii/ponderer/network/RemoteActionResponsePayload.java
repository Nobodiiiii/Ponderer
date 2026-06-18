package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.ui.ProjectorConfigScreen;
import com.nododiiiii.ponderer.ui.RemoteBrowserScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

public record RemoteActionResponsePayload(boolean success, String message, boolean refreshCatalog) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(success());
        buf.writeUtf(message() == null ? "" : message());
        buf.writeBoolean(refreshCatalog());
    }

    public static RemoteActionResponsePayload decode(FriendlyByteBuf buf) {
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
