package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.ponder.UploadPermissions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

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

    public static void handle(PermissionUpdateRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }

        UploadPermissions.UpdateResult result;
        if ("remove".equals(payload.action())) {
            result = UploadPermissions.remove(player, payload.subject());
        } else {
            UploadPermissions.Role role = UploadPermissions.Role.fromId(payload.role());
            result = UploadPermissions.upsert(player, payload.subject(), role);
        }

        PermissionListResponsePayload.sendSnapshot(
            player,
            result.messageKey(),
            result.subject(),
            !result.success());
    }
}
