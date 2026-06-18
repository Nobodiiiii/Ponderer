package com.nododiiiii.ponderer.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public record ProjectorFeatureConfigRequestPayload() {

    public void encode(FriendlyByteBuf buf) {
    }

    public static ProjectorFeatureConfigRequestPayload decode(FriendlyByteBuf buf) {
        return new ProjectorFeatureConfigRequestPayload();
    }

    public static void handle(ProjectorFeatureConfigRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        ProjectorFeatureConfigResponsePayload.sendCurrentState(player, "", false);
    }
}
