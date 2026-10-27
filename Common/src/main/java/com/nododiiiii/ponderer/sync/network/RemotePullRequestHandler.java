package com.nododiiiii.ponderer.sync.network;

import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.sync.server.RemoteWorkspaceService;
import com.nododiiiii.ponderer.sync.server.UploadPermissions;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import com.nododiiiii.ponderer.sync.protocol.RemoteActionResponsePayload;
import com.nododiiiii.ponderer.sync.protocol.SyncResponsePayload;
import com.nododiiiii.ponderer.sync.protocol.RemotePullRequestPayload;
import static com.nododiiiii.ponderer.sync.protocol.RemotePullRequestPayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class RemotePullRequestHandler {
    private RemotePullRequestHandler() {}

    private static final int MAX_SYNC_PAYLOAD_BYTES = 900 * 1024;

    public static void handle(RemotePullRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        UploadPermissions.ensurePullAccess(player);
        if (!UploadPermissions.canPull(player)) {
            PondererServices.NETWORK.sendToPlayer(player,
                new RemoteActionResponsePayload(false, net.minecraft.network.chat.Component.translatable("ponderer.cmd.pull.no_permission"), false));
            return;
        }

        RemoteWorkspaceService.PullBundle bundle = RemoteWorkspaceService.collectPullBundle(
            player.server, payload.kind(), payload.id(), payload.pack(), payload.includeDependencies());
        if (bundle.scripts().isEmpty() && bundle.structures().isEmpty()) {
            PondererServices.NETWORK.sendToPlayer(player,
                new RemoteActionResponsePayload(false, bundle.message(), false));
            return;
        }

        // Choosing a specific remote resource is an explicit request to replace its local copy.
        String mode = RemoteWorkspaceService.KIND_SCENE.equals(payload.kind())
            || RemoteWorkspaceService.KIND_STRUCTURE.equals(payload.kind()) ? "force" : "check";
        sendBatched(player, bundle, mode);
        PondererServices.NETWORK.sendToPlayer(player, new RemoteActionResponsePayload(true, bundle.message(), false));
    }

    private static void sendBatched(ServerPlayer player, RemoteWorkspaceService.PullBundle bundle, String mode) {
        List<SyncResponsePayload.FileEntry> scriptBatch = new ArrayList<>();
        List<SyncResponsePayload.FileEntry> structureBatch = new ArrayList<>();
        int currentBytes = 0;

        for (SyncResponsePayload.FileEntry entry : bundle.scripts()) {
            currentBytes = queueEntry(player, entry, scriptBatch, structureBatch, true, currentBytes, mode);
        }
        for (SyncResponsePayload.FileEntry entry : bundle.structures()) {
            currentBytes = queueEntry(player, entry, scriptBatch, structureBatch, false, currentBytes, mode);
        }

        PondererServices.NETWORK.sendToPlayer(player,
            new SyncResponsePayload(List.copyOf(scriptBatch), List.copyOf(structureBatch), List.of(), List.of(),
                true, 0, mode, "remote_pull"));
    }

    private static int queueEntry(ServerPlayer player, SyncResponsePayload.FileEntry entry,
                                  List<SyncResponsePayload.FileEntry> scripts,
                                  List<SyncResponsePayload.FileEntry> structures,
                                  boolean script, int currentBytes, String mode) {
        int entrySize = entry.bytes() == null ? 0 : entry.bytes().length;
        if (currentBytes > 0 && currentBytes + entrySize > MAX_SYNC_PAYLOAD_BYTES) {
            PondererServices.NETWORK.sendToPlayer(player,
                new SyncResponsePayload(List.copyOf(scripts), List.copyOf(structures), List.of(), List.of(),
                    false, 0, mode, "remote_pull"));
            scripts.clear();
            structures.clear();
            currentBytes = 0;
        }
        if (script) {
            scripts.add(entry);
        } else {
            structures.add(entry);
        }
        return currentBytes + entrySize;
    }
}
