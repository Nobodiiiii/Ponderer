package com.nododiiiii.ponderer.sync.protocol;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PermissionListRequestPayload() implements CustomPacketPayload {

    public static final Type<PermissionListRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "permission_list_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PermissionListRequestPayload> CODEC =
            StreamCodec.of(PermissionListRequestPayload::encode, PermissionListRequestPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, PermissionListRequestPayload payload) {
    }

    private static PermissionListRequestPayload decode(RegistryFriendlyByteBuf buf) {
        return new PermissionListRequestPayload();
    }


}
