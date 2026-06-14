package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.FeatureAvailability;
import com.nododiiiii.ponderer.platform.PondererServices;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record FeatureAvailabilityPayload(boolean blueprintEnabled, boolean projectorEnabled) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(blueprintEnabled());
        buf.writeBoolean(projectorEnabled());
    }

    public static FeatureAvailabilityPayload decode(FriendlyByteBuf buf) {
        return new FeatureAvailabilityPayload(buf.readBoolean(), buf.readBoolean());
    }

    public static void sendTo(ServerPlayer player) {
        PondererServices.NETWORK.sendToPlayer(player, new FeatureAvailabilityPayload(
            FeatureAvailability.isBlueprintEnabled(),
            FeatureAvailability.isProjectorEnabled()));
    }

    public static void handle(FeatureAvailabilityPayload payload) {
        FeatureAvailability.captureFromServer(payload.blueprintEnabled(), payload.projectorEnabled());
    }
}
