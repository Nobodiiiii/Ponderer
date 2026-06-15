package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.RemoteWorkspaceService;
import com.nododiiiii.ponderer.ponder.UploadPermissions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public record RemoteCatalogRequestPayload() {
    public void encode(FriendlyByteBuf buf) {
    }

    public static RemoteCatalogRequestPayload decode(FriendlyByteBuf buf) {
        return new RemoteCatalogRequestPayload();
    }

    public static void handle(RemoteCatalogRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        UploadPermissions.ensurePullAccess(player);
        RemoteWorkspaceService.CatalogSnapshot snapshot = RemoteWorkspaceService.catalog(player.server, player);
        PondererServices.NETWORK.sendToPlayer(player, RemoteCatalogResponsePayload.fromSnapshot(snapshot, "", false));
    }
}
