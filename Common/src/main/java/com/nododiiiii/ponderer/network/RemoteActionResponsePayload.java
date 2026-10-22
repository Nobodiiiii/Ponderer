package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.feedback.FeedbackLevel;
import com.nododiiiii.ponderer.feedback.ClientFeedback;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.ui.ProjectorConfigScreen;
import com.nododiiiii.ponderer.ui.RemoteBrowserScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import com.nododiiiii.ponderer.network.compat.StreamCodec;
import com.nododiiiii.ponderer.network.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RemoteActionResponsePayload(boolean success, net.minecraft.network.chat.Component message, boolean refreshCatalog) implements CustomPacketPayload {

    public static final Type<RemoteActionResponsePayload> TYPE =
        new Type<>(new ResourceLocation(Ponderer.MODID, "remote_action_response"));
    public static final StreamCodec<FriendlyByteBuf, RemoteActionResponsePayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), RemoteActionResponsePayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(success());
        buf.writeComponent(message());
        buf.writeBoolean(refreshCatalog());
    }

    public static RemoteActionResponsePayload decode(FriendlyByteBuf buf) {
        return new RemoteActionResponsePayload(buf.readBoolean(), buf.readComponent(), buf.readBoolean());
    }

    public static void handle(RemoteActionResponsePayload payload) {
        Minecraft client = Minecraft.getInstance();
        ClientFeedback.result(payload.message(), payload.success() ? FeedbackLevel.SUCCESS : FeedbackLevel.ERROR);
        if (ClientFeedback.responseScreen() instanceof RemoteBrowserScreen screen && client.screen == screen) {
            screen.receiveAction(payload);
        } else if (ClientFeedback.responseScreen() instanceof ProjectorConfigScreen screen && client.screen == screen) {
            screen.receiveRemoteAction(payload);
        }
    }
}
