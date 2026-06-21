package com.nododiiiii.ponderer.network;

import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.PondererClientCommands;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ponder.SyncMeta;
import com.nododiiiii.ponderer.projector.client.ProjectorClientCaches;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record SyncResponsePayload(List<FileEntry> scripts, List<FileEntry> structures,
                                  List<DeleteEntry> deletedScripts, List<DeleteEntry> deletedStructures,
                                  boolean finalChunk, int serverSkippedCount, String mode, String reason) {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_SYNC_FILE_BYTES = 768 * 1024;
    private static final int MAX_SYNC_PAYLOAD_BYTES = 900 * 1024;

    @Nullable
    private static PullSession activeSession;

    public record FileEntry(String id, @Nullable String pack, byte[] bytes) {
    }

    public record DeleteEntry(String id, @Nullable String pack) {
    }

    public SyncResponsePayload(List<FileEntry> scripts, List<FileEntry> structures, boolean finalChunk,
                               int serverSkippedCount) {
        this(scripts, structures, List.of(), List.of(), finalChunk, serverSkippedCount, "", "");
    }

    private static final class PullSession {
        private final String pullMode;
        private final String reason;
        private int written;
        private int deleted;
        private int skipped;
        private int conflicts;
        private final Map<String, byte[]> syncedHashes = new HashMap<>();
        private final List<String> deletedMetaKeys = new ArrayList<>();

        private PullSession(String pullMode, String reason) {
            this.pullMode = pullMode;
            this.reason = reason == null ? "" : reason;
        }

        private boolean automatic() {
            return "auto".equals(reason) || "join".equals(reason);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(scripts().size());
        for (FileEntry entry : scripts()) {
            writeEntry(buf, entry);
        }
        buf.writeVarInt(structures().size());
        for (FileEntry entry : structures()) {
            writeEntry(buf, entry);
        }
        buf.writeVarInt(deletedScripts().size());
        for (DeleteEntry entry : deletedScripts()) {
            writeDeleteEntry(buf, entry);
        }
        buf.writeVarInt(deletedStructures().size());
        for (DeleteEntry entry : deletedStructures()) {
            writeDeleteEntry(buf, entry);
        }
        buf.writeBoolean(finalChunk());
        buf.writeVarInt(serverSkippedCount());
        buf.writeUtf(mode() == null ? "" : mode());
        buf.writeUtf(reason() == null ? "" : reason());
    }

    public static SyncResponsePayload decode(FriendlyByteBuf buf) {
        int scriptsSize = buf.readVarInt();
        List<FileEntry> scripts = new ArrayList<>(scriptsSize);
        for (int i = 0; i < scriptsSize; i++) {
            scripts.add(readEntry(buf));
        }
        int structuresSize = buf.readVarInt();
        List<FileEntry> structures = new ArrayList<>(structuresSize);
        for (int i = 0; i < structuresSize; i++) {
            structures.add(readEntry(buf));
        }
        int deletedScriptsSize = buf.readVarInt();
        List<DeleteEntry> deletedScripts = new ArrayList<>(deletedScriptsSize);
        for (int i = 0; i < deletedScriptsSize; i++) {
            deletedScripts.add(readDeleteEntry(buf));
        }
        int deletedStructuresSize = buf.readVarInt();
        List<DeleteEntry> deletedStructures = new ArrayList<>(deletedStructuresSize);
        for (int i = 0; i < deletedStructuresSize; i++) {
            deletedStructures.add(readDeleteEntry(buf));
        }
        boolean finalChunk = buf.readBoolean();
        int serverSkippedCount = buf.readVarInt();
        String mode = buf.readUtf();
        String reason = buf.readUtf();
        return new SyncResponsePayload(scripts, structures, deletedScripts, deletedStructures, finalChunk,
            serverSkippedCount, mode, reason);
    }

    public static void sendBatched(ServerPlayer player) {
        if (player == null) {
            return;
        }

        List<SceneStore.SyncFileRef> scripts = SceneStore.collectServerScriptRefs(player.server);
        List<SceneStore.SyncFileRef> structures = SceneStore.collectServerStructureRefs(player.server);
        List<FileEntry> scriptBatch = new ArrayList<>();
        List<FileEntry> structureBatch = new ArrayList<>();
        int currentBytes = 0;
        int skippedByServer = 0;

        for (SceneStore.SyncFileRef ref : scripts) {
            QueueResult result = queueFile(player, ref, scriptBatch, structureBatch, true, currentBytes);
            currentBytes = result.currentBytes();
            skippedByServer += result.skippedByServer();
        }
        for (SceneStore.SyncFileRef ref : structures) {
            QueueResult result = queueFile(player, ref, scriptBatch, structureBatch, false, currentBytes);
            currentBytes = result.currentBytes();
            skippedByServer += result.skippedByServer();
        }

        PondererServices.NETWORK.sendToPlayer(player,
                new SyncResponsePayload(List.copyOf(scriptBatch), List.copyOf(structureBatch), true, skippedByServer));
    }

    public static void handle(SyncResponsePayload payload) {
        PullSession session = activeSession;
        if (session == null) {
            String pullMode = payload.mode() == null || payload.mode().isBlank()
                ? PondererClientCommands.consumePullMode()
                : payload.mode();
            session = new PullSession(pullMode, payload.reason());
            activeSession = session;
        }

        applyEntries(payload.scripts(), "scripts", ".json", session);
        applyEntries(payload.structures(), "structures", ".nbt", session);
        applyDeletes(payload.deletedScripts(), "scripts", ".json", session);
        applyDeletes(payload.deletedStructures(), "structures", ".nbt", session);
        session.skipped += payload.serverSkippedCount();

        if (!payload.finalChunk()) {
            return;
        }

        if (!session.syncedHashes.isEmpty()) {
            SyncMeta.recordHashes(session.syncedHashes);
        }
        if (!session.deletedMetaKeys.isEmpty()) {
            SyncMeta.removeHashes(session.deletedMetaKeys);
        }

        SceneStore.reloadFromDisk();
        Minecraft.getInstance().execute(ProjectorClientCaches::reloadPonderIndexAndInvalidate);

        if (!session.automatic() || session.written > 0 || session.deleted > 0 || session.skipped > 0
            || session.conflicts > 0) {
            String key = session.automatic() ? "ponderer.cmd.pull.auto_done" : "ponderer.cmd.pull.done";
            notifyClient(Component.translatable(key, session.written + session.deleted, session.skipped,
                session.conflicts));
        }
        if (!session.automatic() && session.conflicts > 0 && "check".equals(session.pullMode)) {
            notifyClient(Component.translatable("ponderer.cmd.pull.hint_force"));
            notifyClient(Component.translatable("ponderer.cmd.pull.hint_keep"));
        }

        activeSession = null;
    }

    private static void applyDeletes(List<DeleteEntry> entries, String category, String ext, PullSession session) {
        for (DeleteEntry entry : entries) {
            Path localFile = resolveLocalPath(new FileEntry(entry.id(), entry.pack(), new byte[0]), ext);
            String displayId = displayId(entry.id(), entry.pack());
            if (localFile == null) {
                LOGGER.warn("Rejected unsafe {} delete path from server: {} pack={}", category, entry.id(), entry.pack());
                session.skipped++;
                continue;
            }

            String metaKey = SyncMeta.metaKey(category, entry.id(), entry.pack());
            String status = SyncMeta.checkDeleteConflict(metaKey, localFile);
            if ("local_modified".equals(status)) {
                session.conflicts++;
                session.skipped++;
                if (!session.automatic()) {
                    notifyClient(Component.translatable("ponderer.cmd.pull.delete_conflict", displayId));
                }
                continue;
            }

            if (!Files.exists(localFile)) {
                session.deletedMetaKeys.add(metaKey);
                continue;
            }

            try {
                Files.deleteIfExists(localFile);
                session.deletedMetaKeys.add(metaKey);
                session.deleted++;
            } catch (Exception e) {
                LOGGER.warn("Failed to delete synced file: {}", localFile, e);
                session.skipped++;
                notifyClient(Component.translatable("ponderer.cmd.pull.delete_failed", displayId));
            }
        }
    }

    private static void applyEntries(List<FileEntry> entries, String category, String ext, PullSession session) {
        for (FileEntry entry : entries) {
            Path localFile = resolveLocalPath(entry, ext);
            String displayId = displayId(entry.id(), entry.pack());
            if (localFile == null) {
                LOGGER.warn("Rejected unsafe {} path from server: {} pack={}", category, entry.id(), entry.pack());
                session.skipped++;
                continue;
            }

            String metaKey = SyncMeta.metaKey(category, entry.id(), entry.pack());
            if (!"force".equals(session.pullMode)) {
                String status = SyncMeta.checkConflict(metaKey, entry.bytes(), localFile);
                if ("both_modified".equals(status)) {
                    session.conflicts++;
                    if ("check".equals(session.pullMode)) {
                        notifyClient(Component.translatable("ponderer.cmd.pull.conflict_both", displayId));
                        session.skipped++;
                        continue;
                    }
                    if ("keep_local".equals(session.pullMode)) {
                        session.skipped++;
                        continue;
                    }
                    notifyClient(Component.translatable("ponderer.cmd.pull.conflict_server", displayId));
                } else if ("local_modified".equals(status) && "keep_local".equals(session.pullMode)) {
                    session.skipped++;
                    continue;
                }
            }

            if (hasSameLocalContent(localFile, entry.bytes())) {
                session.syncedHashes.put(metaKey, entry.bytes());
                continue;
            }

            if (writeFile(localFile, entry.bytes())) {
                session.syncedHashes.put(metaKey, entry.bytes());
                session.written++;
            } else {
                session.skipped++;
                notifyClient(Component.translatable("ponderer.cmd.pull.write_failed", displayId));
            }
        }
    }

    static boolean hasSameLocalContent(@Nullable Path localFile, byte[] remoteBytes) {
        if (localFile == null || !Files.exists(localFile)) {
            return false;
        }
        String localHash = SyncMeta.hashLocalFile(localFile);
        return !localHash.isEmpty() && localHash.equals(SyncMeta.sha256(remoteBytes));
    }

    @Nullable
    private static Path resolveLocalPath(FileEntry entry, String ext) {
        if (".json".equals(ext)) {
            return SceneStore.findLocalSceneFile(entry.id(), entry.pack());
        }
        ResourceLocation loc = ResourceLocation.tryParse(entry.id());
        if (loc == null) {
            return null;
        }
        return SceneStore.resolveLocalSyncStructurePath(loc, entry.pack());
    }

    private static boolean writeFile(Path path, byte[] bytes) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to write file: {}", path, e);
            return false;
        }
    }

    private static QueueResult queueFile(ServerPlayer player, SceneStore.SyncFileRef ref, List<FileEntry> scriptBatch,
                                         List<FileEntry> structureBatch, boolean script, int currentBytes) {
        long size;
        try {
            size = Files.size(ref.path());
        } catch (Exception e) {
            player.sendSystemMessage(Component.translatable("ponderer.cmd.pull.server_skip_failed",
                    displayId(ref.id(), ref.pack())));
            return new QueueResult(currentBytes, 1);
        }

        if (size > MAX_SYNC_FILE_BYTES) {
            player.sendSystemMessage(Component.translatable("ponderer.cmd.pull.server_skip_too_large",
                    displayId(ref.id(), ref.pack()), size));
            return new QueueResult(currentBytes, 1);
        }

        if (currentBytes > 0 && currentBytes + size > MAX_SYNC_PAYLOAD_BYTES) {
            flushBatch(player, scriptBatch, structureBatch, false, 0);
            currentBytes = 0;
        }

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(ref.path());
        } catch (Exception e) {
            player.sendSystemMessage(Component.translatable("ponderer.cmd.pull.server_skip_failed",
                    displayId(ref.id(), ref.pack())));
            return new QueueResult(currentBytes, 1);
        }

        if (bytes.length > MAX_SYNC_FILE_BYTES) {
            player.sendSystemMessage(Component.translatable("ponderer.cmd.pull.server_skip_too_large",
                    displayId(ref.id(), ref.pack()), bytes.length));
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
                                   boolean finalChunk, int skippedByServer) {
        if (scriptBatch.isEmpty() && structureBatch.isEmpty() && !finalChunk) {
            return;
        }
        PondererServices.NETWORK.sendToPlayer(player,
                new SyncResponsePayload(List.copyOf(scriptBatch), List.copyOf(structureBatch), finalChunk, skippedByServer));
        scriptBatch.clear();
        structureBatch.clear();
    }

    private static void notifyClient(Component message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(message, false);
        }
    }

    private static String displayId(String id, @Nullable String pack) {
        if (pack == null || pack.isBlank()) {
            return id;
        }
        return "[" + pack + "] " + id;
    }

    private static void writeEntry(FriendlyByteBuf buf, FileEntry entry) {
        buf.writeUtf(entry.id());
        writeOptionalUtf(buf, entry.pack());
        buf.writeByteArray(entry.bytes());
    }

    private static void writeDeleteEntry(FriendlyByteBuf buf, DeleteEntry entry) {
        buf.writeUtf(entry.id());
        writeOptionalUtf(buf, entry.pack());
    }

    private static FileEntry readEntry(FriendlyByteBuf buf) {
        return new FileEntry(buf.readUtf(), readOptionalUtf(buf), buf.readByteArray());
    }

    private static DeleteEntry readDeleteEntry(FriendlyByteBuf buf) {
        return new DeleteEntry(buf.readUtf(), readOptionalUtf(buf));
    }

    private static void writeOptionalUtf(FriendlyByteBuf buf, @Nullable String value) {
        boolean present = value != null && !value.isBlank();
        buf.writeBoolean(present);
        if (present) {
            buf.writeUtf(value);
        }
    }

    @Nullable
    private static String readOptionalUtf(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUtf() : null;
    }

    private record QueueResult(int currentBytes, int skippedByServer) {
    }
}
