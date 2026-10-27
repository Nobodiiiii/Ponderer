package com.nododiiiii.ponderer.features.blueprint.protocol;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BlueprintConfigUpdatePayload(boolean enableBuiltinItem) implements CustomPacketPayload {

    public static final Type<BlueprintConfigUpdatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "blueprint_config_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintConfigUpdatePayload> CODEC =
            StreamCodec.of(BlueprintConfigUpdatePayload::encode, BlueprintConfigUpdatePayload::decode);


    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, BlueprintConfigUpdatePayload payload) {
        buf.writeBoolean(payload.enableBuiltinItem());
    }

    private static BlueprintConfigUpdatePayload decode(RegistryFriendlyByteBuf buf) {
        return new BlueprintConfigUpdatePayload(buf.readBoolean());
    }


}
