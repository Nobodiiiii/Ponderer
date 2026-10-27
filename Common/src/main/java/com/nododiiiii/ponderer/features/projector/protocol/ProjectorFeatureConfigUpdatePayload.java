package com.nododiiiii.ponderer.features.projector.protocol;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ProjectorFeatureConfigUpdatePayload(boolean enableProjector) implements CustomPacketPayload {

    public static final Type<ProjectorFeatureConfigUpdatePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "projector_feature_config_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectorFeatureConfigUpdatePayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), ProjectorFeatureConfigUpdatePayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(enableProjector());
    }

    public static ProjectorFeatureConfigUpdatePayload decode(RegistryFriendlyByteBuf buf) {
        return new ProjectorFeatureConfigUpdatePayload(buf.readBoolean());
    }


}
