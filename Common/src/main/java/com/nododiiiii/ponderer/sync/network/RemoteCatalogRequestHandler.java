package com.nododiiiii.ponderer.sync.network;

import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.sync.server.RemoteWorkspaceService;
import com.nododiiiii.ponderer.application.security.UploadPermissions;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import com.nododiiiii.ponderer.sync.protocol.RemoteCatalogRequestPayload;
import static com.nododiiiii.ponderer.sync.protocol.RemoteCatalogRequestPayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class RemoteCatalogRequestHandler {
    private RemoteCatalogRequestHandler() {}

    public static void handle(RemoteCatalogRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        UploadPermissions.ensurePullAccess(player);
        RemoteWorkspaceService.CatalogSnapshot snapshot = RemoteWorkspaceService.catalog(player.server, player);
        PondererServices.NETWORK.sendToPlayer(player, RemoteCatalogResponseHandler.fromSnapshot(snapshot, "", false));
    }
}
