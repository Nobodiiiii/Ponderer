package com.nododiiiii.ponderer.ponder;

import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.network.SyncResponsePayload;
import com.nododiiiii.ponderer.platform.PondererServices;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Throttled server-side broadcaster for remote Ponderer workspace changes.
 */
public final class AutoRemoteSyncService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int DIRTY_DELAY_TICKS = 20;
    private static final int MIN_SCAN_INTERVAL_TICKS = 100;
    private static final int BACKGROUND_POLL_INTERVAL_TICKS = 60 * 20;
    private static final int ACTIVE_TRIGGER_COOLDOWN_TICKS = 60 * 20;
    private static final int PLAYER_PAYLOAD_COOLDOWN_TICKS = 40;
    private static final int MAX_SYNC_FILE_BYTES = 768 * 1024;
    private static final int MAX_SYNC_PAYLOAD_BYTES = 900 * 1024;

    private static final Map<UUID, PlayerSyncState> PLAYER_STATES = new LinkedHashMap<>();
    private static Map<ResourceKey, IndexedResource> index = Map.of();
    private static final LinkedHashSet<ResourceKey> recentDeletedKeys = new LinkedHashSet<>();
    private static boolean dirty = true;
    private static int tickCounter;
    private static int dirtySinceTick = 0;
    private static int lastScanTick = -MIN_SCAN_INTERVAL_TICKS;
    private static int lastActiveTriggerTick = -ACTIVE_TRIGGER_COOLDOWN_TICKS;

    private AutoRemoteSyncService() {
    }

    public static void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (!dirty && tickCounter - lastScanTick >= BACKGROUND_POLL_INTERVAL_TICKS) {
            markDirty();
        }
        if (dirty && tickCounter - dirtySinceTick >= DIRTY_DELAY_TICKS
            && tickCounter - lastScanTick >= MIN_SCAN_INTERVAL_TICKS) {
            ScanResult result = rebuildIndex(server);
            if (!result.changed().isEmpty() || !result.deleted().isEmpty()) {
                enqueueForOnlinePullers(server, result.changed(), result.deleted(), "auto");
            }
        }
        dispatchOne(server);
    }

    public static void onRemoteWorkspaceChanged(MinecraftServer server) {
        if (tickCounter - lastActiveTriggerTick < ACTIVE_TRIGGER_COOLDOWN_TICKS) {
            return;
        }
        lastActiveTriggerTick = tickCounter;
        markDirty();
    }

    public static void onPlayerJoined(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UploadPermissions.ensurePullAccess(player);
        if (!UploadPermissions.canPull(player)) {
            return;
        }
        PlayerSyncState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), PlayerSyncState::new);
        if (index.isEmpty()) {
            markDirty();
        }
        enqueueChanged(state, keysNeedingSync(state, index.values()), "join");
        enqueueDeleted(state, recentDeletedKeys, "join");
    }

    public static void onPlayerLeft(UUID playerId) {
        if (playerId != null) {
            PLAYER_STATES.remove(playerId);
        }
    }

    public static void reset() {
        PLAYER_STATES.clear();
        recentDeletedKeys.clear();
        index = Map.of();
        dirty = true;
        tickCounter = 0;
        dirtySinceTick = 0;
        lastScanTick = -MIN_SCAN_INTERVAL_TICKS;
        lastActiveTriggerTick = -ACTIVE_TRIGGER_COOLDOWN_TICKS;
    }

    private static void markDirty() {
        if (!dirty) {
            dirtySinceTick = tickCounter;
        }
        dirty = true;
    }

    private static ScanResult rebuildIndex(MinecraftServer server) {
        Map<ResourceKey, IndexedResource> previous = index;
        Map<ResourceKey, IndexedResource> next = new LinkedHashMap<>();
        addRefs(next, previous, "scripts", true, SceneStore.collectServerScriptRefs(server));
        addRefs(next, previous, "structures", false, SceneStore.collectServerStructureRefs(server));

        Set<ResourceKey> changed = new LinkedHashSet<>();
        for (IndexedResource resource : next.values()) {
            IndexedResource old = previous.get(resource.key());
            if (old == null || !Objects.equals(old.hash(), resource.hash())) {
                changed.add(resource.key());
            }
        }

        Set<ResourceKey> deleted = new LinkedHashSet<>(previous.keySet());
        deleted.removeAll(next.keySet());
        recentDeletedKeys.removeAll(next.keySet());
        recentDeletedKeys.addAll(deleted);
        trimRecentDeletedKeys();

        index = Map.copyOf(next);
        dirty = false;
        lastScanTick = tickCounter;
        return new ScanResult(changed, deleted);
    }

    private static void addRefs(Map<ResourceKey, IndexedResource> next, Map<ResourceKey, IndexedResource> previous,
                                String category, boolean script, List<SceneStore.SyncFileRef> refs) {
        for (SceneStore.SyncFileRef ref : refs) {
            if (ref == null || ref.id() == null || ref.id().isBlank() || ref.path() == null) {
                continue;
            }
            ResourceKey key = new ResourceKey(category, ref.id(), ref.pack(), script);
            IndexedResource old = previous.get(key);
            IndexedResource resource = inspectResource(key, ref.path(), old);
            if (resource != null) {
                next.put(key, resource);
            }
        }
    }

    @Nullable
    private static IndexedResource inspectResource(ResourceKey key, Path path, @Nullable IndexedResource old) {
        try {
            long size = Files.size(path);
            long modifiedTime = Files.getLastModifiedTime(path).toMillis();
            if (old != null && old.path().equals(path) && old.size() == size && old.modifiedTime() == modifiedTime) {
                return old;
            }

            if (size > MAX_SYNC_FILE_BYTES) {
                return new IndexedResource(key, path, "too_large:" + size + ":" + modifiedTime, size,
                    modifiedTime, null, "too_large");
            }

            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length > MAX_SYNC_FILE_BYTES) {
                return new IndexedResource(key, path, "too_large:" + bytes.length + ":" + modifiedTime,
                    bytes.length, modifiedTime, null, "too_large");
            }
            return new IndexedResource(key, path, SyncMeta.sha256(bytes), bytes.length, modifiedTime, bytes, "");
        } catch (Exception e) {
            LOGGER.warn("Failed to inspect remote sync resource {}", path, e);
            return old == null ? null : new IndexedResource(key, path,
                "read_failed:" + old.hash() + ":" + tickCounter, old.size(), old.modifiedTime(), null, "read_failed");
        }
    }

    private static void enqueueForOnlinePullers(MinecraftServer server, Collection<ResourceKey> changed,
                                                Collection<ResourceKey> deleted, String reason) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UploadPermissions.ensurePullAccess(player);
            if (!UploadPermissions.canPull(player)) {
                continue;
            }
            PlayerSyncState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), PlayerSyncState::new);
            enqueueChanged(state, changed, reason);
            enqueueDeleted(state, deleted, reason);
        }
    }

    private static Collection<ResourceKey> keysNeedingSync(PlayerSyncState state,
                                                           Collection<IndexedResource> resources) {
        List<ResourceKey> keys = new ArrayList<>();
        for (IndexedResource resource : resources) {
            String lastHash = state.lastSentHashes.get(resource.key());
            if (!Objects.equals(lastHash, resource.hash())) {
                keys.add(resource.key());
            }
        }
        return keys;
    }

    private static void enqueueChanged(PlayerSyncState state, Collection<ResourceKey> keys, String reason) {
        for (ResourceKey key : keys) {
            IndexedResource resource = index.get(key);
            if (resource == null || Objects.equals(state.lastSentHashes.get(key), resource.hash())) {
                continue;
            }
            state.pendingDeletes.remove(key);
            state.pendingChanges.add(key);
        }
        if (!keys.isEmpty()) {
            state.reason = reason;
        }
    }

    private static void enqueueDeleted(PlayerSyncState state, Collection<ResourceKey> keys, String reason) {
        for (ResourceKey key : keys) {
            state.pendingChanges.remove(key);
            state.pendingDeletes.add(key);
        }
        if (!keys.isEmpty()) {
            state.reason = reason;
        }
    }

    private static void trimRecentDeletedKeys() {
        while (recentDeletedKeys.size() > 1024) {
            recentDeletedKeys.remove(recentDeletedKeys.iterator().next());
        }
    }

    private static void dispatchOne(MinecraftServer server) {
        for (UUID playerId : new ArrayList<>(PLAYER_STATES.keySet())) {
            PlayerSyncState state = PLAYER_STATES.remove(playerId);
            if (state == null) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            PLAYER_STATES.put(playerId, state);
            if (state.isIdle() || tickCounter < state.nextAllowedTick) {
                continue;
            }
            if (!UploadPermissions.canPull(player)) {
                state.clearPending();
                continue;
            }
            SyncResponsePayload payload = buildPayload(player, state);
            if (payload == null) {
                continue;
            }
            PondererServices.NETWORK.sendToPlayer(player, payload);
            state.nextAllowedTick = tickCounter + PLAYER_PAYLOAD_COOLDOWN_TICKS;
            if (payload.finalChunk()) {
                state.reason = "";
            }
            return;
        }
    }

    @Nullable
    private static SyncResponsePayload buildPayload(ServerPlayer player, PlayerSyncState state) {
        List<SyncResponsePayload.FileEntry> scripts = new ArrayList<>();
        List<SyncResponsePayload.FileEntry> structures = new ArrayList<>();
        List<SyncResponsePayload.DeleteEntry> deletedScripts = new ArrayList<>();
        List<SyncResponsePayload.DeleteEntry> deletedStructures = new ArrayList<>();
        int currentBytes = 0;
        int skipped = 0;

        while (!state.pendingDeletes.isEmpty()) {
            ResourceKey key = first(state.pendingDeletes);
            state.pendingDeletes.remove(key);
            SyncResponsePayload.DeleteEntry entry = new SyncResponsePayload.DeleteEntry(key.id(), key.pack());
            if (key.script()) {
                deletedScripts.add(entry);
            } else {
                deletedStructures.add(entry);
            }
            state.lastSentHashes.remove(key);
        }

        while (!state.pendingChanges.isEmpty()) {
            ResourceKey key = first(state.pendingChanges);
            IndexedResource resource = index.get(key);
            if (resource == null) {
                state.pendingChanges.remove(key);
                continue;
            }
            if (Objects.equals(state.lastSentHashes.get(key), resource.hash())) {
                state.pendingChanges.remove(key);
                continue;
            }
            if ("too_large".equals(resource.skipReason())) {
                player.sendSystemMessage(Component.translatable("ponderer.cmd.pull.server_skip_too_large",
                    displayId(key), resource.size()));
                state.pendingChanges.remove(key);
                state.lastSentHashes.put(key, resource.hash());
                skipped++;
                continue;
            }
            if (resource.bytes() == null) {
                player.sendSystemMessage(Component.translatable("ponderer.cmd.pull.server_skip_failed", displayId(key)));
                state.pendingChanges.remove(key);
                state.lastSentHashes.put(key, resource.hash());
                skipped++;
                continue;
            }
            if (currentBytes > 0 && currentBytes + resource.bytes().length > MAX_SYNC_PAYLOAD_BYTES) {
                break;
            }

            SyncResponsePayload.FileEntry entry =
                new SyncResponsePayload.FileEntry(key.id(), key.pack(), resource.bytes());
            if (key.script()) {
                scripts.add(entry);
            } else {
                structures.add(entry);
            }
            currentBytes += resource.bytes().length;
            state.pendingChanges.remove(key);
            state.lastSentHashes.put(key, resource.hash());
        }

        if (scripts.isEmpty() && structures.isEmpty() && deletedScripts.isEmpty() && deletedStructures.isEmpty()
            && skipped == 0) {
            return null;
        }

        boolean finalChunk = state.pendingChanges.isEmpty() && state.pendingDeletes.isEmpty();
        return new SyncResponsePayload(List.copyOf(scripts), List.copyOf(structures), List.copyOf(deletedScripts),
            List.copyOf(deletedStructures), finalChunk, skipped, "check",
            state.reason == null || state.reason.isBlank() ? "auto" : state.reason);
    }

    private static ResourceKey first(LinkedHashSet<ResourceKey> keys) {
        return keys.iterator().next();
    }

    private static String displayId(ResourceKey key) {
        return SceneStore.displaySceneKey(key.id(), key.pack());
    }

    private record ResourceKey(String category, String id, @Nullable String pack, boolean script) {
    }

    private record IndexedResource(ResourceKey key, Path path, String hash, long size, long modifiedTime,
                                   @Nullable byte[] bytes, String skipReason) {
    }

    private record ScanResult(Set<ResourceKey> changed, Set<ResourceKey> deleted) {
    }

    private static final class PlayerSyncState {
        private final Map<ResourceKey, String> lastSentHashes = new LinkedHashMap<>();
        private final LinkedHashSet<ResourceKey> pendingChanges = new LinkedHashSet<>();
        private final LinkedHashSet<ResourceKey> pendingDeletes = new LinkedHashSet<>();
        private int nextAllowedTick;
        private String reason = "";

        private PlayerSyncState(UUID playerId) {
        }

        private boolean isIdle() {
            return pendingChanges.isEmpty() && pendingDeletes.isEmpty();
        }

        private void clearPending() {
            pendingChanges.clear();
            pendingDeletes.clear();
            reason = "";
        }
    }
}
