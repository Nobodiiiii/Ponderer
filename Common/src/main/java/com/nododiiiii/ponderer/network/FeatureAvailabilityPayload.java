package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.FeatureAvailability;
import com.nododiiiii.ponderer.platform.PondererServices;
import net.minecraft.network.FriendlyByteBuf;
import com.nododiiiii.ponderer.network.compat.StreamCodec;
import com.nododiiiii.ponderer.network.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record FeatureAvailabilityPayload(boolean blueprintEnabled, boolean projectorEnabled) implements CustomPacketPayload {

    public static final Type<FeatureAvailabilityPayload> TYPE =
        new Type<>(new ResourceLocation(Ponderer.MODID, "feature_availability"));
    public static final StreamCodec<FriendlyByteBuf, FeatureAvailabilityPayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), FeatureAvailabilityPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


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
