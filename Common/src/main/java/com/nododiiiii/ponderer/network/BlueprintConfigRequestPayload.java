package com.nododiiiii.ponderer.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public record BlueprintConfigRequestPayload() {

    public void encode(FriendlyByteBuf buf) {
    }

    public static BlueprintConfigRequestPayload decode(FriendlyByteBuf buf) {
        return new BlueprintConfigRequestPayload();
    }

    public static void handle(BlueprintConfigRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        BlueprintConfigResponsePayload.sendCurrentState(player, "", false);
    }
}
