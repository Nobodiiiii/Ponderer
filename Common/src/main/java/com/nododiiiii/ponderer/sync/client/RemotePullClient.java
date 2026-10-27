package com.nododiiiii.ponderer.sync.client;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.feedback.FeedbackLevel;
import com.nododiiiii.ponderer.feedback.ClientFeedback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.nododiiiii.ponderer.sync.protocol.SyncResponsePayload;
import static com.nododiiiii.ponderer.sync.protocol.SyncResponsePayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class RemotePullClient {
    private RemotePullClient() {}

    public static void resetConnection() {
        automaticSession = null;
        manualSession = null;
        sessionConnection = null;
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable
    private static PullSession automaticSession;

    @Nullable
    private static PullSession manualSession;

    @Nullable
    private static Object sessionConnection;

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

    public static void handle(SyncResponsePayload payload) {
        if (!SyncClientModule.isActive()) return;
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

    public record OverwriteResult(int applied, @Nullable RuntimeException refreshFailure) {}

    public static OverwriteResult overwritePending(List<PendingRemoteUpdates.Update> updates) {
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
        RuntimeException refreshFailure = finishSession(session);
        return new OverwriteResult(selected - session.skipped, refreshFailure);
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

    @Nullable
    private static RuntimeException finishSession(PullSession session) {
        persistSessionMetadata(session);

        RuntimeException refreshFailure = session.written > 0 || session.deleted > 0
            ? SyncClientEvents.workspaceChanged() : null;
        if (refreshFailure != null) {
            LOGGER.error("Remote updates saved, but Ponder refresh failed; /ponderer reload can retry", refreshFailure);
        }
        SyncClientEvents.updatesChanged();

        if (!session.automatic() || session.written > 0 || session.deleted > 0 || session.skipped > 0
            || session.conflicts > 0) {
            String key = session.automatic() ? "ponderer.cmd.pull.auto_done" : "ponderer.cmd.pull.done";
            Component summary = Component.translatable(key, session.written + session.deleted, session.skipped, session.conflicts);
            if (refreshFailure != null) {
                summary = summary.copy().append("\n").append(Component.translatable(
                    "ponderer.ui.save_error.refresh_after_save", refreshFailure.getMessage()));
            }
            if (session.automatic()) ClientFeedback.chat(summary);
            else ClientFeedback.result(summary,
                refreshFailure != null ? FeedbackLevel.ERROR
                    : session.skipped > 0 || session.conflicts > 0 ? FeedbackLevel.WARNING : FeedbackLevel.SUCCESS);
        }
        if (!session.automatic() && session.conflicts > 0 && "check".equals(session.pullMode)) {
            notifyClient(Component.translatable("ponderer.cmd.pull.hint_force"));
            notifyClient(Component.translatable("ponderer.cmd.pull.hint_keep"));
        }
        if (session.conflicts > 0 && !session.automatic()) {
            notifyClient(Component.translatable("ponderer.cmd.pull.hint_remote_manager"));
        }
        return refreshFailure;
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

    public static boolean hasSameLocalContent(@Nullable Path localFile, byte[] remoteBytes) {
        if (localFile == null || !Files.exists(localFile)) {
            return false;
        }
        String localHash = SyncMeta.hashLocalFile(localFile);
        return !localHash.isEmpty() && localHash.equals(SyncMeta.sha256(remoteBytes));
    }

    @Nullable
    private static Path resolveLocalPath(FileEntry entry, String ext) {
        if (".json".equals(ext)) {
            return com.nododiiiii.ponderer.workspace.api.WorkspaceAccess.get().scenes().findLocalSceneFile(entry.id(), entry.pack());
        }
        ResourceLocation loc = ResourceLocation.tryParse(entry.id());
        if (loc == null) {
            return null;
        }
        return com.nododiiiii.ponderer.workspace.api.WorkspaceAccess.get().layout().resolveLocalSyncStructurePath(loc, entry.pack());
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
}
