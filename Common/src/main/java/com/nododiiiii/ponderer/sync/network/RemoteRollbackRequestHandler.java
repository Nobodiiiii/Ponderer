package com.nododiiiii.ponderer.sync.network;

import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.sync.server.RemoteWorkspaceService;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import com.nododiiiii.ponderer.sync.protocol.RemoteActionResponsePayload;
import com.nododiiiii.ponderer.sync.protocol.RemoteRollbackRequestPayload;
import static com.nododiiiii.ponderer.sync.protocol.RemoteRollbackRequestPayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class RemoteRollbackRequestHandler {
    private RemoteRollbackRequestHandler() {}

    public static void handle(RemoteRollbackRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        RemoteWorkspaceService.OperationResult result = RemoteWorkspaceService.rollback(
            player.server, player, payload.kind(), payload.id(), payload.pack(), payload.revision());
        PondererServices.NETWORK.sendToPlayer(player,
            new RemoteActionResponsePayload(result.success(), result.message(), result.refreshCatalog()));
    }
}
