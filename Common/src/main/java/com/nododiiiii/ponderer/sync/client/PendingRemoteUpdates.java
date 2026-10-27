package com.nododiiiii.ponderer.sync.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client-side conflicts for the current connection; ignored versions survive reconnects. */
public final class PendingRemoteUpdates {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Update> PENDING = new LinkedHashMap<>();
    private static Map<String, String> ignored = new HashMap<>();
    @Nullable
    private static Object connection;
    @Nullable
    private static Path ignoredPath;
    private static int revision;

    public record Update(String category, String id, @Nullable String pack, @Nullable byte[] bytes,
                         String conflict) {
        public String key() {
            return SyncMeta.metaKey(category, id, pack);
        }

        public String version() {
            return bytes == null ? "deleted" : SyncMeta.sha256(bytes);
        }

        public boolean deletion() {
            return bytes == null;
        }
    }

    public static void resetConnection() {
        connection = null;
        PENDING.clear();
        ignored = new HashMap<>();
        ignoredPath = null;
        revision++;
    }

    private PendingRemoteUpdates() {
    }

    public static List<Update> list() {
        ensureContext();
        return List.copyOf(PENDING.values());
    }

    public static int revision() {
        ensureContext();
        return revision;
    }

    public static boolean isIgnored(Update update) {
        ensureContext();
        String version = ignored.get(update.key());
        if (update.version().equals(version)) {
            return true;
        }
        if (version != null) {
            // Once a different version is observed, the old decision has expired.
            ignored.remove(update.key());
            saveIgnored();
        }
        return false;
    }

    public static void remember(Update update) {
        ensureContext();
        PENDING.put(update.key(), update);
        revision++;
    }

    public static int ignore(List<Update> updates) {
        ensureContext();
        int count = 0;
        for (Update update : updates) {
            if (PENDING.get(update.key()) != update) {
                continue;
            }
            ignored.put(update.key(), update.version());
            PENDING.remove(update.key());
            count++;
        }
        if (count > 0) {
            revision++;
            saveIgnored();
        }
        return count;
    }

    public static void resolved(String key) {
        ensureContext();
        if (PENDING.remove(key) != null) {
            revision++;
        }
        if (ignored.remove(key) != null) {
            saveIgnored();
        }
    }

    private static void ensureContext() {
        Minecraft mc = Minecraft.getInstance();
        Object current = mc.getConnection();
        if (connection == current) {
            return;
        }
        connection = current;
        PENDING.clear();
        ignored = new HashMap<>();
        ignoredPath = null;
        revision++;
        if (current == null) {
            return;
        }
        String source;
        if (mc.getSingleplayerServer() != null) {
            source = "world:" + mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT)
                .toAbsolutePath().normalize();
        } else if (mc.getCurrentServer() != null) {
            source = "server:" + mc.getCurrentServer().ip;
        } else {
            return;
        }
        ignoredPath = com.nododiiiii.ponderer.workspace.api.WorkspaceAccess.get().layout().getSceneDir().getParent().resolve(".sync_ignored")
            .resolve(SyncMeta.sha256(source.getBytes(StandardCharsets.UTF_8)) + ".json");
        if (!Files.exists(ignoredPath)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(ignoredPath)) {
            Map<String, String> loaded = GSON.fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
            if (loaded != null) {
                ignored.putAll(loaded);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read ignored remote updates: {}", ignoredPath, e);
        }
    }

    private static void saveIgnored() {
        if (ignoredPath == null) {
            return;
        }
        try {
            Files.createDirectories(ignoredPath.getParent());
            try (Writer writer = Files.newBufferedWriter(ignoredPath)) {
                GSON.toJson(ignored, writer);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to write ignored remote updates: {}", ignoredPath, e);
        }
    }
}
