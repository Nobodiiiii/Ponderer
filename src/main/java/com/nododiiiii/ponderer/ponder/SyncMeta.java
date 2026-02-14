package com.nododiiiii.ponderer.ponder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Tracks SHA-256 hashes of files at their last sync point, enabling
 * conflict detection between local and server versions.
 *
 * Storage: config/ponderer/.sync_hashes.json
 * Format:  { "scripts/example.json": "abcdef...", "structures/castle.nbt": "123456..." }
 */
public final class SyncMeta {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String META_FILE = ".sync_hashes.json";

    private SyncMeta() {
    }

    private static Path getMetaPath() {
        return SceneStore.getSceneDir().getParent().resolve(META_FILE);
    }

    public static Map<String, String> load() {
        Path path = getMetaPath();
        if (!Files.exists(path)) {
            return new HashMap<>();
        }
        try (Reader r = Files.newBufferedReader(path)) {
            Map<String, String> map = GSON.fromJson(r, new TypeToken<Map<String, String>>() {}.getType());
            return map == null ? new HashMap<>() : new HashMap<>(map);
        } catch (Exception e) {
            LOGGER.warn("Failed to read sync meta: {}", path, e);
            return new HashMap<>();
        }
    }

    public static void save(Map<String, String> hashes) {
        Path path = getMetaPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(hashes, w);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to write sync meta: {}", path, e);
        }
    }

    /**
     * Update the sync meta for a single key after a successful sync.
     */
    public static void recordHash(String key, byte[] content) {
        Map<String, String> meta = load();
        meta.put(key, sha256(content));
        save(meta);
    }

    /**
     * Update hashes for multiple keys at once.
     */
    public static void recordHashes(Map<String, byte[]> entries) {
        Map<String, String> meta = load();
        for (var e : entries.entrySet()) {
            meta.put(e.getKey(), sha256(e.getValue()));
        }
        save(meta);
    }

    public static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Read a local file and compute its hash.
     */
    public static String hashLocalFile(Path file) {
        if (!Files.exists(file)) return "";
        try {
            return sha256(Files.readAllBytes(file));
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Check conflict status when pulling a file.
     *
     * @return "none" if no conflict, "local_modified" if only local changed,
     * "server_modified" if only server changed, "both_modified" if conflict
     */
    public static String checkConflict(String metaKey, byte[] serverContent, Path localFile) {
        Map<String, String> meta = load();
        String lastSyncHash = meta.getOrDefault(metaKey, "");
        String serverHash = sha256(serverContent);
        String localHash = hashLocalFile(localFile);

        if (localHash.isEmpty()) {
            // Local file doesn't exist -> no conflict, just accept server
            return "none";
        }

        boolean localChanged = !localHash.equals(lastSyncHash);
        boolean serverChanged = !serverHash.equals(lastSyncHash);

        if (localChanged && serverChanged) {
            // Both sides changed since last sync
            if (localHash.equals(serverHash)) {
                return "none"; // Both made the same change
            }
            return "both_modified";
        }
        if (localChanged) {
            return "local_modified";
        }
        return "none"; // Server changed or nothing changed
    }
}
