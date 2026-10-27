package com.nododiiiii.ponderer.features.projector.protocol;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ProjectorFeatureConfigResponsePayload(boolean enableProjector, boolean canManage,
                                                    String messageKey, boolean error) implements CustomPacketPayload {

    public static final Type<ProjectorFeatureConfigResponsePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "projector_feature_config_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectorFeatureConfigResponsePayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), ProjectorFeatureConfigResponsePayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(enableProjector());
        buf.writeBoolean(canManage());
        buf.writeUtf(messageKey() == null ? "" : messageKey());
        buf.writeBoolean(error());
    }

    public static ProjectorFeatureConfigResponsePayload decode(RegistryFriendlyByteBuf buf) {
        return new ProjectorFeatureConfigResponsePayload(
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readUtf(),
            buf.readBoolean());
    }

}
