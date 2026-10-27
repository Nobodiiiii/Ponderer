package com.nododiiiii.ponderer.application.protocol;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FeatureAvailabilityPayload(boolean blueprintEnabled, boolean projectorEnabled) implements CustomPacketPayload {

    public static final Type<FeatureAvailabilityPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "feature_availability"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FeatureAvailabilityPayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), FeatureAvailabilityPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(blueprintEnabled());
        buf.writeBoolean(projectorEnabled());
    }

    public static FeatureAvailabilityPayload decode(RegistryFriendlyByteBuf buf) {
        return new FeatureAvailabilityPayload(buf.readBoolean(), buf.readBoolean());
    }

}
