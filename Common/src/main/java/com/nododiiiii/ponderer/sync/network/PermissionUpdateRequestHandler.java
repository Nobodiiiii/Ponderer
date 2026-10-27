package com.nododiiiii.ponderer.sync.network;

import com.nododiiiii.ponderer.sync.server.UploadPermissions;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import com.nododiiiii.ponderer.sync.network.PermissionListResponseHandler;
import com.nododiiiii.ponderer.sync.protocol.PermissionUpdateRequestPayload;
import static com.nododiiiii.ponderer.sync.protocol.PermissionUpdateRequestPayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class PermissionUpdateRequestHandler {
    private PermissionUpdateRequestHandler() {}

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

        PermissionListResponseHandler.sendSnapshot(
            player,
            result.messageKey(),
            result.subject(),
            !result.success());
    }
}
