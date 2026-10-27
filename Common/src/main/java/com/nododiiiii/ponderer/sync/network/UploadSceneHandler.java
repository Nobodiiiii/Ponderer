package com.nododiiiii.ponderer.sync.network;

import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.sync.server.RemoteWorkspaceService;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.sync.client.SyncMeta;
import com.nododiiiii.ponderer.sync.server.UploadPermissions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import com.nododiiiii.ponderer.sync.protocol.UploadResponsePayload;
import com.nododiiiii.ponderer.sync.protocol.UploadScenePayload;
import static com.nododiiiii.ponderer.sync.protocol.UploadScenePayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class UploadSceneHandler {
    private UploadSceneHandler() {}

    public static void handle(UploadScenePayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!UploadPermissions.canUpload(player)) {
            PondererServices.NETWORK.sendToPlayer(player, new UploadResponsePayload(payload.sceneId(), payload.pack(), "denied"));
            return;
        }

        String pushMode = payload.mode() == null ? "check" : payload.mode();
        String displayId = SceneStore.displaySceneKey(payload.sceneId(), payload.pack());

        if (!"force".equals(pushMode)) {
            String lastSyncHash = payload.lastSyncHash() == null ? "" : payload.lastSyncHash();
            String serverHash = computeServerSceneHash(player.server, payload.sceneId(), payload.pack());

            if (!serverHash.isEmpty() && !lastSyncHash.isEmpty() && !serverHash.equals(lastSyncHash)) {

                PondererServices.NETWORK.sendToPlayer(player,
                        new UploadResponsePayload(payload.sceneId(), payload.pack(), "conflict"));
                return;
            }
        }

        RemoteWorkspaceService.OperationResult result = RemoteWorkspaceService.uploadScene(player, payload);
        boolean ok = result.success();

        if (ok) {

            PondererServices.NETWORK.sendToPlayer(player,
                    new UploadResponsePayload(payload.sceneId(), payload.pack(), "ok:" + result.hash()));
        } else {

            PondererServices.NETWORK.sendToPlayer(player,
                    new UploadResponsePayload(payload.sceneId(), payload.pack(), "error"));
        }
    }

    private static String computeServerSceneHash(net.minecraft.server.MinecraftServer server, String sceneId,
            @Nullable String pack) {
        ResourceLocation loc = ResourceLocation.tryParse(sceneId);
        if (loc == null) return "";
        java.nio.file.Path path = SceneStore.resolveServerScenePath(server, loc, pack);
        if (path == null) return "";
        if (!java.nio.file.Files.exists(path)) return "";
        try {
            return SyncMeta.sha256(java.nio.file.Files.readAllBytes(path));
        } catch (Exception e) {
            return "";
        }
    }
}
