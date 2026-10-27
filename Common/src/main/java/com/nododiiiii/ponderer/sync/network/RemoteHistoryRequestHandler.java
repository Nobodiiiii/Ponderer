package com.nododiiiii.ponderer.sync.network;

import com.nododiiiii.ponderer.sync.network.RemoteHistoryResponseHandler;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.sync.server.RemoteWorkspaceService;
import com.nododiiiii.ponderer.sync.server.UploadPermissions;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import com.nododiiiii.ponderer.sync.protocol.RemoteHistoryRequestPayload;
import static com.nododiiiii.ponderer.sync.protocol.RemoteHistoryRequestPayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class RemoteHistoryRequestHandler {
    private RemoteHistoryRequestHandler() {}

    public static void handle(RemoteHistoryRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        UploadPermissions.ensurePullAccess(player);
        List<RemoteWorkspaceService.HistoryEntry> entries = RemoteWorkspaceService.history(
            player.server, payload.kind(), payload.id(), payload.pack());
        PondererServices.NETWORK.sendToPlayer(player,
            RemoteHistoryResponseHandler.fromEntries(payload.kind(), payload.id(), payload.pack(),
                entries, UploadPermissions.canManage(player)));
    }
}
