package com.nododiiiii.ponderer.ponder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Represents metadata for a Ponderer resource pack.
 */
public class PonderPackInfo {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();

    public String name;           // e.g., "MyPack"
    public String version;        // e.g., "1.0.0"
    public String author;         // e.g., "PlayerName"
    public String description;    // Pack description
    public Path sourcePath;       // e.g., resourcepacks/[Ponderer] MyPack.zip
    public String packPrefix;     // e.g., "[MyPack]"
    public long lastModified;     // File modification time

    public PonderPackInfo() {
    }

    public PonderPackInfo(String name, String version, String author, String description, Path sourcePath) {
        this.name = name;
        this.version = version;
        this.author = author;
        this.description = description;
        this.sourcePath = sourcePath;
        this.packPrefix = "[" + name + "]";
        try {
            this.lastModified = Files.getLastModifiedTime(sourcePath).toMillis();
        } catch (IOException e) {
            this.lastModified = 0;
        }
    }

    /**
     * Load PonderPackInfo from a zip file's pack.json
     */
    public static PonderPackInfo fromZip(Path zipPath) {
        if (!Files.exists(zipPath)) {
            return null;
        }

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("pack.json".equals(entry.getName())) {
                    try (InputStreamReader reader = new InputStreamReader(zis, StandardCharsets.UTF_8)) {
                        JsonObject root = GSON.fromJson(reader, JsonObject.class);
                        if (root == null || !root.has("ponderer")) {
                            LOGGER.warn("pack.json missing 'ponderer' field in {}", zipPath);
                            return null;
                        }

                        JsonObject ponderData = root.getAsJsonObject("ponderer");
                        PonderPackInfo info = new PonderPackInfo();
                        info.name = getStringOrDefault(ponderData, "name", "Unknown");
                        info.version = getStringOrDefault(ponderData, "version", "1.0.0");
                        info.author = getStringOrDefault(ponderData, "author", "Unknown");
                        info.description = getStringOrDefault(ponderData, "description", "");
                        info.sourcePath = zipPath;
                        info.packPrefix = "[" + info.name + "]";
                        try {
                            info.lastModified = Files.getLastModifiedTime(zipPath).toMillis();
                        } catch (IOException ignored) {
                            info.lastModified = 0;
                        }
                        return info;
                    }
                }
            }
            LOGGER.warn("pack.json not found in {}", zipPath);
            return null;
        } catch (Exception e) {
            LOGGER.warn("Failed to read PonderPackInfo from {}: {}", zipPath, e.getMessage());
            return null;
        }
    }

    private static String getStringOrDefault(JsonObject obj, String key, String defaultValue) {
        if (obj.has(key)) {
            JsonElement elem = obj.get(key);
            if (elem.isJsonPrimitive()) {
                return elem.getAsString();
            }
        }
        return defaultValue;
    }

    @Override
    public String toString() {
        return name + " v" + version + (author != null && !author.isEmpty() ? " by " + author : "");
    }
}
