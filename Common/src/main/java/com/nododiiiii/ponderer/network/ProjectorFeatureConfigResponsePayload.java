package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.feedback.FeedbackLevel;
import com.nododiiiii.ponderer.feedback.ClientFeedback;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.UploadPermissions;
import com.nododiiiii.ponderer.ui.ProjectorFeatureConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import com.nododiiiii.ponderer.network.compat.StreamCodec;
import com.nododiiiii.ponderer.network.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public record ProjectorFeatureConfigResponsePayload(boolean enableProjector, boolean canManage,
                                                    String messageKey, boolean error) implements CustomPacketPayload {

    public static final Type<ProjectorFeatureConfigResponsePayload> TYPE =
        new Type<>(new ResourceLocation(Ponderer.MODID, "projector_feature_config_response"));
    public static final StreamCodec<FriendlyByteBuf, ProjectorFeatureConfigResponsePayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), ProjectorFeatureConfigResponsePayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(enableProjector());
        buf.writeBoolean(canManage());
        buf.writeUtf(messageKey() == null ? "" : messageKey());
        buf.writeBoolean(error());
    }

    public static ProjectorFeatureConfigResponsePayload decode(FriendlyByteBuf buf) {
        return new ProjectorFeatureConfigResponsePayload(
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readUtf(),
            buf.readBoolean());
    }

    public static void sendCurrentState(ServerPlayer player, String messageKey, boolean error) {
        PondererServices.NETWORK.sendToPlayer(player, new ProjectorFeatureConfigResponsePayload(
            currentEnableProjector(),
            UploadPermissions.canManage(player),
            messageKey == null ? "" : messageKey,
            error));
    }

    public static void handle(ProjectorFeatureConfigResponsePayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (ClientFeedback.responseScreen() instanceof ProjectorFeatureConfigScreen screen) {
            screen.receiveServerState(payload);
            return;
        }

        if (payload.messageKey() != null && !payload.messageKey().isBlank() && client.player != null) {
            ClientFeedback.result(Component.translatable(payload.messageKey()),
                payload.error() ? FeedbackLevel.ERROR : FeedbackLevel.SUCCESS);
        }
    }

    private static boolean currentEnableProjector() {
        try {
            return Config.ENABLE_PROJECTOR.get();
        } catch (Exception ignored) {
            return false;
        }
    }
}
