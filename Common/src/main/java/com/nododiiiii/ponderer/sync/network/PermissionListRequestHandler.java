package com.nododiiiii.ponderer.sync.network;

import com.nododiiiii.ponderer.sync.network.PermissionListResponseHandler;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import com.nododiiiii.ponderer.sync.protocol.PermissionListRequestPayload;
import static com.nododiiiii.ponderer.sync.protocol.PermissionListRequestPayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class PermissionListRequestHandler {
    private PermissionListRequestHandler() {}

    public static void handle(PermissionListRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        PermissionListResponseHandler.sendSnapshot(player, "", "", false);
    }
}
