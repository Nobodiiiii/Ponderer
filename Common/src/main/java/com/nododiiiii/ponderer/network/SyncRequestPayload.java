package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.ponder.UploadPermissions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public record SyncRequestPayload() {
    public void encode(net.minecraft.network.FriendlyByteBuf buf) {
    }

    public static SyncRequestPayload decode(net.minecraft.network.FriendlyByteBuf buf) {
        return new SyncRequestPayload();
    }

    public static void handle(SyncRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!UploadPermissions.canPull(player)) {
            player.sendSystemMessage(Component.translatable("ponderer.cmd.pull.no_permission"));
            return;
        }
        SyncResponsePayload.sendBatched(player);
    }
}
