package com.nododiiiii.ponderer.sync.protocol;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PermissionUpdateRequestPayload(String action, String subject, String role) implements CustomPacketPayload {

    public static final Type<PermissionUpdateRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "permission_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PermissionUpdateRequestPayload> CODEC =
            StreamCodec.of(PermissionUpdateRequestPayload::encode, PermissionUpdateRequestPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, PermissionUpdateRequestPayload payload) {
        buf.writeUtf(payload.action() == null ? "" : payload.action());
        buf.writeUtf(payload.subject() == null ? "" : payload.subject());
        buf.writeUtf(payload.role() == null ? "" : payload.role());
    }

    private static PermissionUpdateRequestPayload decode(RegistryFriendlyByteBuf buf) {
        return new PermissionUpdateRequestPayload(buf.readUtf(), buf.readUtf(), buf.readUtf());
    }


}
