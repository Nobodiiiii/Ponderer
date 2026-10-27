package com.nododiiiii.ponderer.features.blueprint.protocol;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BlueprintConfigResponsePayload(boolean enableBuiltinItem, boolean canManage,
                                             String messageKey, boolean error) implements CustomPacketPayload {

    public static final Type<BlueprintConfigResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "blueprint_config_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintConfigResponsePayload> CODEC =
            StreamCodec.of(BlueprintConfigResponsePayload::encode, BlueprintConfigResponsePayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, BlueprintConfigResponsePayload payload) {
        buf.writeBoolean(payload.enableBuiltinItem());
        buf.writeBoolean(payload.canManage());
        buf.writeUtf(payload.messageKey() == null ? "" : payload.messageKey());
        buf.writeBoolean(payload.error());
    }

    private static BlueprintConfigResponsePayload decode(RegistryFriendlyByteBuf buf) {
        return new BlueprintConfigResponsePayload(
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readUtf(),
            buf.readBoolean());
    }

}
