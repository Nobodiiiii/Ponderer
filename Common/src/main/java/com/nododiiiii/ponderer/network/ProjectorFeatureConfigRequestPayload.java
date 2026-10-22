package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.network.FriendlyByteBuf;
import com.nododiiiii.ponderer.network.compat.StreamCodec;
import com.nododiiiii.ponderer.network.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public record ProjectorFeatureConfigRequestPayload() implements CustomPacketPayload {

    public static final Type<ProjectorFeatureConfigRequestPayload> TYPE =
        new Type<>(new ResourceLocation(Ponderer.MODID, "projector_feature_config_request"));
    public static final StreamCodec<FriendlyByteBuf, ProjectorFeatureConfigRequestPayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), ProjectorFeatureConfigRequestPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


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
