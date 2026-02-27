package com.nododiiiii.ponderer.ponder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages the registry of loaded Ponderer packs.
 * Stores metadata in .ponderer_registry.json
 */
public class PonderPackRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String REGISTRY_FILE = ".ponderer_registry.json";

    private static Map<String, PackEntry> registry = new HashMap<>();
    private static Path registryPath;

    static {
        registryPath = FMLPaths.CONFIGDIR.get().resolve("ponderer").resolve(REGISTRY_FILE);
    }

    public static class PackEntry {
        public String name;
        public String version;
        public String author;
        public String description;
        public String sourceFile;      // relative to resourcepacks/
        public String fileHash;         // SHA-256 of zip
        public String loadedAt;         // ISO 8601 timestamp
        public String packPrefix;       // [PackName]

        public PackEntry() {
        }

        public PackEntry(PonderPackInfo info, String fileHash) {
            this.name = info.name;
            this.version = info.version;
            this.author = info.author;
            this.description = info.description;
            this.sourceFile = info.sourcePath.getFileName().toString();
            this.fileHash = fileHash;
            this.loadedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
            this.packPrefix = info.packPrefix;
        }
    }

    /**
     * Load registry from disk
     */
    public static void load() {
        registry.clear();
        if (!Files.exists(registryPath)) {
            LOGGER.info("Registry file not found, creating new registry");
            return;
        }

        try (Reader reader = Files.newBufferedReader(registryPath, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root != null && root.has("packs")) {
                JsonObject packsObj = root.getAsJsonObject("packs");
                for (String key : packsObj.keySet()) {
                    try {
                        PackEntry entry = GSON.fromJson(packsObj.get(key), PackEntry.class);
                        if (entry != null) {
                            registry.put(key, entry);
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Failed to parse pack entry: {}", key, e);
                    }
                }
            }
            LOGGER.info("Loaded {} Ponderer packs from registry", registry.size());
        } catch (IOException e) {
            LOGGER.warn("Failed to load Ponderer registry", e);
        }
    }

    /**
     * Save registry to disk
     */
    public static void save() {
        try {
            Files.createDirectories(registryPath.getParent());
            JsonObject root = new JsonObject();
            JsonObject packsObj = new JsonObject();

            for (String key : registry.keySet()) {
                packsObj.add(key, GSON.toJsonTree(registry.get(key)));
            }
            root.add("packs", packsObj);

            String json = GSON.toJson(root);
            Files.writeString(registryPath, json, StandardCharsets.UTF_8);
            LOGGER.info("Saved Ponderer registry with {} packs", registry.size());
        } catch (IOException e) {
            LOGGER.error("Failed to save Ponderer registry", e);
        }
    }

    /**
     * Add or update a pack in the registry
     */
    public static void addOrUpdatePack(String displayName, PonderPackInfo info, String fileHash) {
        PackEntry entry = new PackEntry(info, fileHash);
        registry.put(displayName, entry);
        LOGGER.info("Added pack to registry: {}", displayName);
        save();
    }

    /**
     * Remove a pack from the registry
     */
    public static void removePack(String displayName) {
        if (registry.remove(displayName) != null) {
            LOGGER.info("Removed pack from registry: {}", displayName);
            save();
        }
    }

    /**
     * Get a pack entry by display name
     */
    public static PackEntry getPack(String displayName) {
        return registry.get(displayName);
    }

    /**
     * Get all packs in registry
     */
    public static Map<String, PackEntry> getAllPacks() {
        return new HashMap<>(registry);
    }

    /**
     * Check if a pack with this display name is registered
     */
    public static boolean hasPack(String displayName) {
        return registry.containsKey(displayName);
    }

    /**
     * Get the display name for a pack (e.g., "[Ponderer] MyPack")
     */
    public static String getDisplayName(String packName) {
        return "[Ponderer] " + packName;
    }
}
