package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.feedback.FeedbackLevel;
import com.nododiiiii.ponderer.feedback.ServerFeedback;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.ponder.UploadPermissions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public record SyncRequestPayload(String mode) implements CustomPacketPayload {

    public static final Type<SyncRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "sync_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncRequestPayload> CODEC =
            StreamCodec.of(SyncRequestPayload::encode, SyncRequestPayload::decode);

    public SyncRequestPayload() { this("check"); }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, SyncRequestPayload payload) {
        buf.writeUtf(payload.mode());
    }

    private static SyncRequestPayload decode(RegistryFriendlyByteBuf buf) {
        return new SyncRequestPayload(buf.readUtf());
    }

    public static void handle(SyncRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        UploadPermissions.ensurePullAccess(player);
        if (!UploadPermissions.canPull(player)) {
            ServerFeedback.send(player, Component.translatable("ponderer.cmd.pull.no_permission"), FeedbackLevel.ERROR);
            return;
        }
        SyncResponsePayload.sendBatched(player, payload.mode());
    }
}
