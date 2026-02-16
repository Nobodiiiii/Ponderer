package com.nododiiiii.ponderer.ponder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraft.resources.ResourceLocation;
import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class SceneStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setLenient()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter())
        .create();
    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter())
        .create();
    private static final String BASE_DIR = "ponderer";
    private static final String SCRIPT_DIR = "scripts";
    private static final String STRUCTURE_DIR = "structures";

    private SceneStore() {
    }

    public static Path getSceneDir() {
        return FMLPaths.CONFIGDIR.get().resolve(BASE_DIR).resolve(SCRIPT_DIR);
    }

    public static Path getStructureDir() {
        return FMLPaths.CONFIGDIR.get().resolve(BASE_DIR).resolve(STRUCTURE_DIR);
    }

    public static Path getStructurePath(String path) {
        return getStructureDir().resolve(path + ".nbt");
    }

    public static Path getStructurePath(ResourceLocation id) {
        return getStructureDir().resolve(id.getPath() + ".nbt");
    }

    public static Path getServerSceneDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(BASE_DIR).resolve(SCRIPT_DIR);
    }

    public static Path getServerStructureDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(BASE_DIR).resolve(STRUCTURE_DIR);
    }

    public static boolean saveToServer(MinecraftServer server, String sceneId, String json) {
        ResourceLocation sceneLoc = ResourceLocation.tryParse(sceneId);
        if (sceneLoc == null) {
            LOGGER.warn("Invalid scene id: {}", sceneId);
            return false;
        }

        Path sceneDir = getServerSceneDir(server);
        Path scenePath = sceneLoc.getNamespace().equals(Ponderer.MODID)
            ? sceneDir.resolve(sceneLoc.getPath() + ".json")
            : sceneDir.resolve(sceneLoc.getNamespace()).resolve(sceneLoc.getPath() + ".json");

        try {
            Files.createDirectories(scenePath.getParent());
            Files.writeString(scenePath, json);
        } catch (IOException e) {
            LOGGER.error("Failed to write scene json: {}", scenePath, e);
            return false;
        }

        LOGGER.info("Uploaded scene {} to server storage", sceneId);
        return true;
    }

    public static boolean saveStructureToServer(MinecraftServer server, String structureId, byte[] structureBytes) {
        if (structureId == null || structureId.isBlank() || structureBytes == null) {
            return true;
        }

        ResourceLocation structureLoc = ResourceLocation.tryParse(structureId);
        if (structureLoc == null) {
            LOGGER.warn("Invalid structure id: {}", structureId);
            return false;
        }
        Path structureDir = getServerStructureDir(server);
        Path structurePath = structureLoc.getNamespace().equals(Ponderer.MODID)
            ? structureDir.resolve(structureLoc.getPath() + ".nbt")
            : structureDir.resolve(structureLoc.getNamespace()).resolve(structureLoc.getPath() + ".nbt");
        try {
            Files.createDirectories(structurePath.getParent());
            Files.write(structurePath, structureBytes);
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to write structure: {}", structurePath, e);
            return false;
        }
    }

    public static List<com.nododiiiii.ponderer.network.SyncResponsePayload.FileEntry> collectServerScripts(MinecraftServer server) {
        Path root = getServerSceneDir(server);
        if (!Files.exists(root)) {
            return List.of();
        }
        List<com.nododiiiii.ponderer.network.SyncResponsePayload.FileEntry> entries = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".json")).toList()) {
                String id = toId(root, path, ".json");
                if (id == null) continue;
                entries.add(new com.nododiiiii.ponderer.network.SyncResponsePayload.FileEntry(id, Files.readAllBytes(path)));
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to collect server scripts", e);
        }
        return entries;
    }

    public static List<com.nododiiiii.ponderer.network.SyncResponsePayload.FileEntry> collectServerStructures(MinecraftServer server) {
        Path root = getServerStructureDir(server);
        if (!Files.exists(root)) {
            return List.of();
        }
        List<com.nododiiiii.ponderer.network.SyncResponsePayload.FileEntry> entries = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".nbt")).toList()) {
                String id = toId(root, path, ".nbt");
                if (id == null) continue;
                entries.add(new com.nododiiiii.ponderer.network.SyncResponsePayload.FileEntry(id, Files.readAllBytes(path)));
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to collect server structures", e);
        }
        return entries;
    }

    private static String toId(Path root, Path file, String ext) {
        Path rel = root.relativize(file);
        if (rel.getNameCount() < 1) {
            return null;
        }
        String namespace;
        String path;
        if (rel.getNameCount() == 1) {
            namespace = Ponderer.MODID;
            path = rel.getName(0).toString();
        } else {
            namespace = rel.getName(0).toString();
            path = rel.subpath(1, rel.getNameCount()).toString().replace("\\", "/");
        }
        if (path.endsWith(ext)) {
            path = path.substring(0, path.length() - ext.length());
        }
        return namespace + ":" + path;
    }

    /**
     * Save a DslScene to the local config directory.
     * File path follows the same convention as reloadFromDisk expects:
     *   namespace == "ponderer" -> config/ponderer/scripts/{path}.json
     *   otherwise              -> config/ponderer/scripts/{path}.json (flat for now)
     *
     * @param scene the DslScene to serialize and save
     * @return true if saved successfully
     */
    public static boolean saveSceneToLocal(DslScene scene) {
        if (scene == null || scene.id == null || scene.id.isBlank()) {
            LOGGER.warn("Cannot save scene with null/blank id");
            return false;
        }

        ResourceLocation loc = ResourceLocation.tryParse(scene.id);
        if (loc == null) {
            LOGGER.warn("Cannot save scene with invalid id: {}", scene.id);
            return false;
        }

        Path dir = getSceneDir();
        // Use the path part as filename (reloadFromDisk currently only reads flat files in scripts/)
        String filename = loc.getPath().replace('/', '_') + ".json";
        Path filePath = dir.resolve(filename);

        // Also check if there's an existing file that contains this scene id
        Path existingFile = findExistingFile(dir, scene.id);
        if (existingFile != null) {
            filePath = existingFile;
        }

        try {
            Files.createDirectories(filePath.getParent());
            String json = GSON_PRETTY.toJson(scene);
            Files.writeString(filePath, json);
            LOGGER.info("Saved scene {} to {}", scene.id, filePath);
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to save scene {} to {}", scene.id, filePath, e);
            return false;
        }
    }

    /**
     * Find the existing JSON file that contains a scene with the given id.
     */
    private static Path findExistingFile(Path dir, String sceneId) {
        if (!Files.exists(dir)) return null;
        try (Stream<Path> paths = Files.list(dir)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")).toList()) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    DslScene existing = GSON.fromJson(reader, DslScene.class);
                    if (existing != null && sceneId.equals(existing.id)) {
                        return path;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    /**
     * Delete a scene's local JSON file by its id.
     *
     * @param sceneId the scene id
     * @return true if the file was found and deleted
     */
    public static boolean deleteSceneLocal(String sceneId) {
        if (sceneId == null || sceneId.isBlank()) return false;
        Path dir = getSceneDir();
        Path existing = findExistingFile(dir, sceneId);
        if (existing == null) {
            LOGGER.warn("No local file found for scene id: {}", sceneId);
            return false;
        }
        try {
            Files.deleteIfExists(existing);
            LOGGER.info("Deleted scene file: {}", existing);
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to delete scene file: {}", existing, e);
            return false;
        }
    }

    public static void extractDefaultsIfNeeded() {
        Path baseDir = FMLPaths.CONFIGDIR.get().resolve(BASE_DIR);
        Path marker = baseDir.resolve(".initialized");
        if (Files.exists(marker)) return;

        Path scriptsDir = getSceneDir();
        Path structureDir = getStructureDir();
        try {
            Files.createDirectories(scriptsDir);
            Files.createDirectories(structureDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create ponderer directories", e);
            return;
        }

        extractResource("data/ponderer/default_scripts/ponderer_example.json",
            scriptsDir.resolve("ponderer_example.json"));
        extractResource("data/ponderer/default_structures/ponderer_example_1.nbt",
            structureDir.resolve("ponderer_example_1.nbt"));
        extractResource("data/ponderer/default_structures/ponderer_example_2.nbt",
            structureDir.resolve("ponderer_example_2.nbt"));

        try {
            Files.writeString(marker, "initialized");
        } catch (IOException e) {
            LOGGER.warn("Failed to write initialization marker", e);
        }
    }

    private static void extractResource(String resourcePath, Path target) {
        if (Files.exists(target)) return;
        try (InputStream in = SceneStore.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOGGER.warn("Default resource not found in jar: {}", resourcePath);
                return;
            }
            Files.copy(in, target);
            LOGGER.info("Extracted default file: {}", target);
        } catch (IOException e) {
            LOGGER.warn("Failed to extract default file: {}", target, e);
        }
    }

    public static int reloadFromDisk() {
        Path dir = getSceneDir();
        List<DslScene> loaded = new ArrayList<>();

        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("Failed to create ponderer scene directory: {}", dir, e);
            SceneRuntime.setScenes(List.of());
            return 0;
        }

        try (Stream<Path> paths = Files.list(dir)) {
            paths.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                .sorted(Comparator.comparing(Path::toString))
                .forEach(path -> {
                    try (Reader reader = Files.newBufferedReader(path)) {
                        DslScene scene = GSON.fromJson(reader, DslScene.class);
                        if (scene == null || scene.id == null || scene.id.isBlank()) {
                            LOGGER.warn("Skipping invalid scene file (missing id): {}", path);
                            return;
                        }
                        loaded.add(scene);
                    } catch (Exception e) {
                        LOGGER.warn("Failed to read scene file: {}", path, e);
                    }
                });
        } catch (IOException e) {
            LOGGER.error("Failed to list scene directory: {}", dir, e);
        }

        SceneRuntime.setScenes(loaded);
        LOGGER.info("Loaded {} ponderer scene(s) from {}", loaded.size(), dir);
        return loaded.size();
    }
}