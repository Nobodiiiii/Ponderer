package com.nododiiiii.ponderer.sync.server;

import com.nododiiiii.ponderer.feedback.FeedbackLevel;
import com.nododiiiii.ponderer.feedback.ServerFeedback;
import com.nododiiiii.ponderer.platform.PondererServices;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import com.nododiiiii.ponderer.sync.protocol.SyncResponsePayload;
import static com.nododiiiii.ponderer.sync.protocol.SyncResponsePayload.*;

/** Server batching has no client or UI dependencies. */
public final class SyncSender {
    private SyncSender() {}

    private static final int MAX_SYNC_PAYLOAD_BYTES = 900 * 1024;


    private static final int MAX_SYNC_FILE_BYTES = 768 * 1024;


    public static void sendBatched(ServerPlayer player) { sendBatched(player, "check"); }

    public static void sendBatched(ServerPlayer player, String mode) {
        if (player == null) {
            return;
        }

        List<com.nododiiiii.ponderer.workspace.api.WorkspaceResults.SyncFileRef> scripts = com.nododiiiii.ponderer.workspace.api.WorkspaceAccess.get().scenes().collectServerScriptRefs(player.server);
        List<com.nododiiiii.ponderer.workspace.api.WorkspaceResults.SyncFileRef> structures = com.nododiiiii.ponderer.workspace.api.WorkspaceAccess.get().structures().collectServerStructureRefs(player.server);
        List<FileEntry> scriptBatch = new ArrayList<>();
        List<FileEntry> structureBatch = new ArrayList<>();
        int currentBytes = 0;
        int skippedByServer = 0;

        for (com.nododiiiii.ponderer.workspace.api.WorkspaceResults.SyncFileRef ref : scripts) {
            QueueResult result = queueFile(player, ref, scriptBatch, structureBatch, true, currentBytes, mode);
            currentBytes = result.currentBytes();
            skippedByServer += result.skippedByServer();
        }
        for (com.nododiiiii.ponderer.workspace.api.WorkspaceResults.SyncFileRef ref : structures) {
            QueueResult result = queueFile(player, ref, scriptBatch, structureBatch, false, currentBytes, mode);
            currentBytes = result.currentBytes();
            skippedByServer += result.skippedByServer();
        }

        PondererServices.NETWORK.sendToPlayer(player,
                new SyncResponsePayload(List.copyOf(scriptBatch), List.copyOf(structureBatch), List.of(), List.of(), true, skippedByServer, mode, "manual"));
    }

    private static QueueResult queueFile(ServerPlayer player, com.nododiiiii.ponderer.workspace.api.WorkspaceResults.SyncFileRef ref, List<FileEntry> scriptBatch,
                                         List<FileEntry> structureBatch, boolean script, int currentBytes, String mode) {
        long size;
        try {
            size = Files.size(ref.path());
        } catch (Exception e) {
            ServerFeedback.send(player, Component.translatable("ponderer.cmd.pull.server_skip_failed",
                    displayId(ref.id(), ref.pack())), FeedbackLevel.WARNING);
            return new QueueResult(currentBytes, 1);
        }

        if (size > MAX_SYNC_FILE_BYTES) {
            ServerFeedback.send(player, Component.translatable("ponderer.cmd.pull.server_skip_too_large",
                    displayId(ref.id(), ref.pack()), size), FeedbackLevel.WARNING);
            return new QueueResult(currentBytes, 1);
        }

        if (currentBytes > 0 && currentBytes + size > MAX_SYNC_PAYLOAD_BYTES) {
            flushBatch(player, scriptBatch, structureBatch, false, 0, mode);
            currentBytes = 0;
        }

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(ref.path());
        } catch (Exception e) {
            ServerFeedback.send(player, Component.translatable("ponderer.cmd.pull.server_skip_failed",
                    displayId(ref.id(), ref.pack())), FeedbackLevel.WARNING);
            return new QueueResult(currentBytes, 1);
        }

        if (bytes.length > MAX_SYNC_FILE_BYTES) {
            ServerFeedback.send(player, Component.translatable("ponderer.cmd.pull.server_skip_too_large",
                    displayId(ref.id(), ref.pack()), bytes.length), FeedbackLevel.WARNING);
            return new QueueResult(currentBytes, 1);
        }

        FileEntry entry = new FileEntry(ref.id(), ref.pack(), bytes);
        if (script) {
            scriptBatch.add(entry);
        } else {
            structureBatch.add(entry);
        }
        return new QueueResult(currentBytes + bytes.length, 0);
    }

    private static void flushBatch(ServerPlayer player, List<FileEntry> scriptBatch, List<FileEntry> structureBatch,
                                   boolean finalChunk, int skippedByServer, String mode) {
        if (scriptBatch.isEmpty() && structureBatch.isEmpty() && !finalChunk) {
            return;
        }
        PondererServices.NETWORK.sendToPlayer(player,
                new SyncResponsePayload(List.copyOf(scriptBatch), List.copyOf(structureBatch), List.of(), List.of(), finalChunk, skippedByServer, mode, "manual"));
        scriptBatch.clear();
        structureBatch.clear();
    }

    private record QueueResult(int currentBytes, int skippedByServer) {
    }

    private static String displayId(String id, @Nullable String pack) {
        if (pack == null || pack.isBlank()) {
            return id;
        }
        return "[" + pack + "] " + id;
    }
}
