package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public record ProjectorFeatureConfigRequestPayload() implements CustomPacketPayload {

    public static final Type<ProjectorFeatureConfigRequestPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "projector_feature_config_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectorFeatureConfigRequestPayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), ProjectorFeatureConfigRequestPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public void encode(RegistryFriendlyByteBuf buf) {
    }

    public static ProjectorFeatureConfigRequestPayload decode(RegistryFriendlyByteBuf buf) {
        return new ProjectorFeatureConfigRequestPayload();
    }

    public static void handle(ProjectorFeatureConfigRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        ProjectorFeatureConfigResponsePayload.sendCurrentState(player, "", false);
    }
}
