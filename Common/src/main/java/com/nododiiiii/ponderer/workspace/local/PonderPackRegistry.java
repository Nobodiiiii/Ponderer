package com.nododiiiii.ponderer.workspace.local;

import com.nododiiiii.ponderer.workspace.api.PonderPackInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private Map<String, PackEntry> registry = new HashMap<>();
    private Path registryPath;

    private final WorkspaceFiles workspace;
    PonderPackRegistry(WorkspaceFiles workspace) {
        this.workspace = workspace;
        registryPath = workspace.configDir.resolve("ponderer").resolve(REGISTRY_FILE);
    }

    /**
     * Load registry from disk
     */
    public void load() {
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
    public void save() {
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
    public void addOrUpdatePack(String displayName, PonderPackInfo info, String fileHash) {
        PackEntry entry = new PackEntry(info, fileHash);
        registry.put(displayName, entry);
        LOGGER.info("Added pack to registry: {}", displayName);
        save();
    }

    /**
     * Remove a pack from the registry
     */
    public void removePack(String displayName) {
        if (registry.remove(displayName) != null) {
            LOGGER.info("Removed pack from registry: {}", displayName);
            save();
        }
    }

    /**
     * Get a pack entry by display name
     */
    public PackEntry getPack(String displayName) {
        return registry.get(displayName);
    }

    /**
     * Get all packs in registry
     */
    public Map<String, PackEntry> getAllPacks() {
        return new HashMap<>(registry);
    }

    /**
     * Check if a pack with this display name is registered
     */
    public boolean hasPack(String displayName) {
        return registry.containsKey(displayName);
    }

    /**
     * Get the display name for a pack (e.g., "[Ponderer] MyPack")
     */
    public String getDisplayName(String packName) {
        return "[Ponderer] " + packName;
    }
}
