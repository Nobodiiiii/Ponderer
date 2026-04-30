package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public record BlueprintConfigRequestPayload() implements CustomPacketPayload {

    public static final Type<BlueprintConfigRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "blueprint_config_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintConfigRequestPayload> CODEC =
            StreamCodec.of(BlueprintConfigRequestPayload::encode, BlueprintConfigRequestPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, BlueprintConfigRequestPayload payload) {
    }

    private static BlueprintConfigRequestPayload decode(RegistryFriendlyByteBuf buf) {
        return new BlueprintConfigRequestPayload();
    }

    public static void handle(BlueprintConfigRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        BlueprintConfigResponsePayload.sendCurrentState(player, "", false);
    }
}
