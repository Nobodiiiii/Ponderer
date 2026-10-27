package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.feedback.FeedbackLevel;
import com.nododiiiii.ponderer.feedback.ServerFeedback;
import com.nododiiiii.ponderer.feedback.ClientFeedback;
import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.AutoRemoteSyncClient;
import com.nododiiiii.ponderer.ponder.PendingRemoteUpdates;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ponder.SyncMeta;
import com.nododiiiii.ponderer.ui.RemoteBrowserScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
                                  boolean finalChunk, int serverSkippedCount,
                                  String mode, String reason) implements CustomPacketPayload {

    public static final Type<SyncResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "sync_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncResponsePayload> CODEC =
            StreamCodec.of((buf, payload) -> payload.encode(buf), SyncResponsePayload::decode);

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_SYNC_FILE_BYTES = 768 * 1024;
    private static final int MAX_SYNC_PAYLOAD_BYTES = 900 * 1024;

    @Nullable
    private static PullSession automaticSession;
    @Nullable
    private static PullSession manualSession;
    @Nullable
    private static Object sessionConnection;

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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
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

    public static SyncResponsePayload decode(RegistryFriendlyByteBuf buf) {
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

    public static void sendBatched(ServerPlayer player) { sendBatched(player, "check"); }

    public static void sendBatched(ServerPlayer player, String mode) {
        if (player == null) {
            return;
        }

        List<com.nododiiiii.ponderer.workspace.api.WorkspaceResults.SyncFileRef> scripts = SceneStore.collectServerScriptRefs(player.server);
        List<com.nododiiiii.ponderer.workspace.api.WorkspaceResults.SyncFileRef> structures = SceneStore.collectServerStructureRefs(player.server);
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

    public static void handle(SyncResponsePayload payload) {
        Object connection = Minecraft.getInstance().getConnection();
        if (sessionConnection != connection) {
            automaticSession = null;
            manualSession = null;
            sessionConnection = connection;
        }
        boolean automatic = "auto".equals(payload.reason()) || "join".equals(payload.reason());
        if (automatic && !AutoRemoteSyncClient.isEnabled()) {
            stopAutomaticSync();
            return;
        }
        PullSession session = automatic ? automaticSession : manualSession;
        if (session == null) {
            String pullMode = payload.mode() == null || payload.mode().isBlank()
                ? "check"
                : payload.mode();
            session = new PullSession(pullMode, payload.reason());
            if (automatic) {
                automaticSession = session;
            } else {
                manualSession = session;
            }
        }

        applyEntries(payload.scripts(), "scripts", ".json", session);
        applyEntries(payload.structures(), "structures", ".nbt", session);
        applyDeletes(payload.deletedScripts(), "scripts", ".json", session);
        applyDeletes(payload.deletedStructures(), "structures", ".nbt", session);
        session.skipped += payload.serverSkippedCount();
        persistSessionMetadata(session);

        if (!payload.finalChunk()) {
            return;
        }

        finishSession(session);
        if (automatic) {
            automaticSession = null;
        } else {
            manualSession = null;
        }
    }

    public static void stopAutomaticSync() {
        if (sessionConnection != Minecraft.getInstance().getConnection()) {
            automaticSession = null;
            return;
        }
        if (automaticSession != null) {
            finishSession(automaticSession);
            automaticSession = null;
        }
    }

    /** Apply only the remote versions the user explicitly selected in remote management. */
    public static int overwritePending(List<PendingRemoteUpdates.Update> updates) {
        PullSession session = new PullSession("force", "resolve");
        List<PendingRemoteUpdates.Update> pending = PendingRemoteUpdates.list();
        int selected = 0;
        for (PendingRemoteUpdates.Update update : updates) {
            if (!pending.contains(update)) {
                continue;
            }
            selected++;
            String ext = "scripts".equals(update.category()) ? ".json" : ".nbt";
            if (update.deletion()) {
                applyDeletes(List.of(new DeleteEntry(update.id(), update.pack())), update.category(), ext, session);
            } else {
                applyEntries(List.of(new FileEntry(update.id(), update.pack(), update.bytes())),
                    update.category(), ext, session);
            }
        }
        finishSession(session);
        return selected - session.skipped;
    }

    private static void persistSessionMetadata(PullSession session) {
        if (!session.syncedHashes.isEmpty()) {
            SyncMeta.recordHashes(session.syncedHashes);
            session.syncedHashes.clear();
        }
        if (!session.deletedMetaKeys.isEmpty()) {
            SyncMeta.removeHashes(session.deletedMetaKeys);
            session.deletedMetaKeys.clear();
        }
    }

    private static void finishSession(PullSession session) {
        persistSessionMetadata(session);

        if (session.written > 0 || session.deleted > 0) {
            SceneStore.reloadFromDisk();
            Minecraft.getInstance().execute(com.nododiiiii.ponderer.application.SceneRefreshCoordinator::reloadPonderIndex);
        }
        if (Minecraft.getInstance().screen instanceof RemoteBrowserScreen screen) {
            screen.onSyncUpdatesChanged();
        }

        if (!session.automatic() || session.written > 0 || session.deleted > 0 || session.skipped > 0
            || session.conflicts > 0) {
            String key = session.automatic() ? "ponderer.cmd.pull.auto_done" : "ponderer.cmd.pull.done";
            Component summary = Component.translatable(key, session.written + session.deleted, session.skipped, session.conflicts);
            if (session.automatic()) ClientFeedback.chat(summary);
            else ClientFeedback.result(summary,
                session.skipped > 0 || session.conflicts > 0 ? FeedbackLevel.WARNING : FeedbackLevel.SUCCESS);
        }
        if (!session.automatic() && session.conflicts > 0 && "check".equals(session.pullMode)) {
            notifyClient(Component.translatable("ponderer.cmd.pull.hint_force"));
            notifyClient(Component.translatable("ponderer.cmd.pull.hint_keep"));
        }
        if (session.conflicts > 0 && !session.automatic()) {
            notifyClient(Component.translatable("ponderer.cmd.pull.hint_remote_manager"));
        }
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
            PendingRemoteUpdates.Update update =
                new PendingRemoteUpdates.Update(category, entry.id(), entry.pack(), null, "local_modified");
            if (session.automatic() && PendingRemoteUpdates.isIgnored(update)) {
                continue;
            }
            String status = SyncMeta.checkDeleteConflict(metaKey, localFile);
            if ("local_modified".equals(status) && (session.automatic() || !"force".equals(session.pullMode))) {
                PendingRemoteUpdates.remember(update);
                session.conflicts++;
                session.skipped++;
                if (!session.automatic()) notifyClient(Component.translatable("ponderer.cmd.pull.delete_conflict", displayId), FeedbackLevel.WARNING);
                continue;
            }

            if (!Files.exists(localFile)) {
                session.deletedMetaKeys.add(metaKey);
                PendingRemoteUpdates.resolved(metaKey);
                continue;
            }

            try {
                Files.deleteIfExists(localFile);
                session.deletedMetaKeys.add(metaKey);
                PendingRemoteUpdates.resolved(metaKey);
                session.deleted++;
            } catch (Exception e) {
                LOGGER.warn("Failed to delete synced file: {}", localFile, e);
                session.skipped++;
                if (!session.automatic()) notifyClient(Component.translatable("ponderer.cmd.pull.delete_failed", displayId), FeedbackLevel.ERROR);
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
            PendingRemoteUpdates.Update update =
                new PendingRemoteUpdates.Update(category, entry.id(), entry.pack(), entry.bytes(), "");
            if (session.automatic() && PendingRemoteUpdates.isIgnored(update)) {
                continue;
            }
            if (session.automatic() || !"force".equals(session.pullMode)) {
                String status = SyncMeta.checkConflict(metaKey, entry.bytes(), localFile);
                if (!"none".equals(status)) {
                    session.conflicts++;
                    // Joining a world must not replace locally saved edits with an older server copy.
                    if (session.automatic() || "check".equals(session.pullMode)
                        || "keep_local".equals(session.pullMode)) {
                        PendingRemoteUpdates.remember(new PendingRemoteUpdates.Update(
                            category, entry.id(), entry.pack(), entry.bytes(), status));
                        if (session.automatic() || "check".equals(session.pullMode)) {
                            String key = switch (status) {
                                case "local_deleted" -> "ponderer.cmd.pull.conflict_deleted";
                                case "both_modified" -> "ponderer.cmd.pull.conflict_both";
                                default -> "ponderer.cmd.pull.conflict_local";
                            };
                            if (!session.automatic()) notifyClient(Component.translatable(key, displayId), FeedbackLevel.WARNING);
                        }
                        session.skipped++;
                        continue;
                    }
                    if (!session.automatic()) notifyClient(Component.translatable("ponderer.cmd.pull.conflict_server", displayId), FeedbackLevel.WARNING);
                }
            }

            if (hasSameLocalContent(localFile, entry.bytes())) {
                session.syncedHashes.put(metaKey, entry.bytes());
                PendingRemoteUpdates.resolved(metaKey);
                continue;
            }

            if (writeFile(localFile, entry.bytes())) {
                session.syncedHashes.put(metaKey, entry.bytes());
                PendingRemoteUpdates.resolved(metaKey);
                session.written++;
            } else {
                session.skipped++;
                if (!session.automatic()) notifyClient(Component.translatable("ponderer.cmd.pull.write_failed", displayId), FeedbackLevel.ERROR);
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

    private static void notifyClient(Component message) { notifyClient(message, FeedbackLevel.INFO); }

    private static void notifyClient(Component message, FeedbackLevel level) {
        if (Minecraft.getInstance().player != null) {
            ClientFeedback.result(message, level);
        }
    }

    private static String displayId(String id, @Nullable String pack) {
        if (pack == null || pack.isBlank()) {
            return id;
        }
        return "[" + pack + "] " + id;
    }

    private static void writeEntry(RegistryFriendlyByteBuf buf, FileEntry entry) {
        buf.writeUtf(entry.id());
        writeOptionalUtf(buf, entry.pack());
        buf.writeByteArray(entry.bytes());
    }

    private static void writeDeleteEntry(RegistryFriendlyByteBuf buf, DeleteEntry entry) {
        buf.writeUtf(entry.id());
        writeOptionalUtf(buf, entry.pack());
    }

    private static FileEntry readEntry(RegistryFriendlyByteBuf buf) {
        return new FileEntry(buf.readUtf(), readOptionalUtf(buf), buf.readByteArray());
    }

    private static DeleteEntry readDeleteEntry(RegistryFriendlyByteBuf buf) {
        return new DeleteEntry(buf.readUtf(), readOptionalUtf(buf));
    }

    private static void writeOptionalUtf(RegistryFriendlyByteBuf buf, @Nullable String value) {
        boolean present = value != null && !value.isBlank();
        buf.writeBoolean(present);
        if (present) {
            buf.writeUtf(value);
        }
    }

    @Nullable
    private static String readOptionalUtf(RegistryFriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUtf() : null;
    }

    private record QueueResult(int currentBytes, int skippedByServer) {
    }
}
