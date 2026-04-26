package com.nododiiiii.ponderer.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public record PermissionListRequestPayload() {

    public void encode(FriendlyByteBuf buf) {
    }

    public static PermissionListRequestPayload decode(FriendlyByteBuf buf) {
        return new PermissionListRequestPayload();
    }

    public static void handle(PermissionListRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        PermissionListResponsePayload.sendSnapshot(player, "", "", false);
    }
}
