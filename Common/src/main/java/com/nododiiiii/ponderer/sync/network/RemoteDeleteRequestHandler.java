package com.nododiiiii.ponderer.sync.network;

import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.sync.server.RemoteWorkspaceService;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import com.nododiiiii.ponderer.sync.protocol.RemoteActionResponsePayload;
import com.nododiiiii.ponderer.sync.protocol.RemoteDeleteRequestPayload;
import static com.nododiiiii.ponderer.sync.protocol.RemoteDeleteRequestPayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class RemoteDeleteRequestHandler {
    private RemoteDeleteRequestHandler() {}

    public static void handle(RemoteDeleteRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        RemoteWorkspaceService.OperationResult result = RemoteWorkspaceService.delete(
            player.server, player, payload.kind(), payload.id(), payload.pack());
        PondererServices.NETWORK.sendToPlayer(player,
            new RemoteActionResponsePayload(result.success(), result.message(), result.refreshCatalog()));
    }
}
