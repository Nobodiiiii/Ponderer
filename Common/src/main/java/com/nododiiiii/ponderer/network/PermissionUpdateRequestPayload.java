package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.ponder.UploadPermissions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public record PermissionUpdateRequestPayload(String action, String subject, String role) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(action() == null ? "" : action());
        buf.writeUtf(subject() == null ? "" : subject());
        buf.writeUtf(role() == null ? "" : role());
    }

    public static PermissionUpdateRequestPayload decode(FriendlyByteBuf buf) {
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
