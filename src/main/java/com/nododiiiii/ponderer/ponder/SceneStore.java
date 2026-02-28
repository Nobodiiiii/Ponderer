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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class SceneStore {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Information about a pack that was updated during auto-load. */
    public static class PackUpdateInfo {
        public final String packName;
        public final String oldVersion;
        public final String newVersion;
        public final int totalFiles;     // total files extracted
        public final int conflictCount;  // number of user-modified files that were backed up

        public PackUpdateInfo(String packName, String oldVersion, String newVersion, int totalFiles, int conflictCount) {
            this.packName = packName;
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
            this.totalFiles = totalFiles;
            this.conflictCount = conflictCount;
        }
    }
    private static final Gson GSON = new GsonBuilder().setLenient()
        .disableHtmlEscaping()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter())
        .create();
    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter())
        .create();
    private static final String BASE_DIR = "ponderer";
    private static final String SCRIPT_DIR = "scripts";
    private static final String STRUCTURE_DIR = "structures";
    private static final String PACKS_SUBDIR = "_packs";

    private SceneStore() {
    }

    public static Path getSceneDir() {
        return FMLPaths.CONFIGDIR.get().resolve(BASE_DIR).resolve(SCRIPT_DIR);
    }

    public static Path getStructureDir() {
        return FMLPaths.CONFIGDIR.get().resolve(BASE_DIR).resolve(STRUCTURE_DIR);
    }

    public static Path getPackSceneDir(String packName) {
        return getSceneDir().resolve(PACKS_SUBDIR).resolve(packName);
    }

    public static Path getPackStructureDir(String packName) {
        return getStructureDir().resolve(PACKS_SUBDIR).resolve(packName);
    }

    public static Path getStructurePath(String path) {
        return getStructureDir().resolve(path + ".nbt");
    }

    public static Path getStructurePath(ResourceLocation id) {
        return getStructureDir().resolve(id.getPath() + ".nbt");
    }

    /**
     * Resolve a structure file path, considering pack context.
     * 1. If packName is given, check pack subdirectory first
     * 2. Fall back to flat directory
     * 3. Search all pack subdirectories
     */
    @javax.annotation.Nullable
    public static Path resolveStructurePath(String path, @javax.annotation.Nullable String packName) {
        // 1. Check pack subdirectory first
        if (packName != null && !packName.isEmpty()) {
            Path packPath = getPackStructureDir(packName).resolve("[" + packName + "] " + path + ".nbt");
            if (Files.exists(packPath)) return packPath;
        }
        // 2. Fall back to flat directory
        Path flatPath = getStructureDir().resolve(path + ".nbt");
        if (Files.exists(flatPath)) return flatPath;
        // 3. Search all pack subdirectories
        Path packsDir = getStructureDir().resolve(PACKS_SUBDIR);
        if (Files.exists(packsDir)) {
            try (Stream<Path> packDirs = Files.list(packsDir)) {
                for (Path packDir : packDirs.filter(Files::isDirectory).toList()) {
                    try (Stream<Path> files = Files.list(packDir)) {
                        for (Path f : files.toList()) {
                            String fname = f.getFileName().toString();
                            if (fname.endsWith(".nbt")) {
                                // Strip [PackName] prefix and .nbt extension to get the base name
                                String baseName = fname;
                                String prefix = DslScene.extractPackPrefix(fname);
                                if (prefix != null) {
                                    baseName = fname.substring(prefix.length()).trim();
                                }
                                if (baseName.endsWith(".nbt")) {
                                    baseName = baseName.substring(0, baseName.length() - 4);
                                }
                                if (baseName.equals(path)) return f;
                            }
                        }
                    } catch (IOException ignored) {}
                }
            } catch (IOException ignored) {}
        }
        return null;
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
            Files.writeString(scenePath, json, StandardCharsets.UTF_8);
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
     * Routes to pack subdirectory if scene has a pack field.
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

        Path dir;
        String filename;
        if (scene.pack != null && !scene.pack.isEmpty()) {
            dir = getPackSceneDir(scene.pack);
            filename = "[" + scene.pack + "] " + loc.getPath().replace('/', '_') + ".json";
        } else {
            dir = getSceneDir();
            filename = loc.getPath().replace('/', '_') + ".json";
        }
        Path filePath = dir.resolve(filename);

        // Check if there's an existing file that contains this scene id
        Path existingFile = findExistingFileForScene(scene);
        if (existingFile != null) {
            filePath = existingFile;
        }

        try {
            Files.createDirectories(filePath.getParent());
            sanitizeScene(scene);
            String json = GSON_PRETTY.toJson(scene);
            Files.writeString(filePath, json, StandardCharsets.UTF_8);
            LOGGER.info("Saved scene {} to {}", scene.id, filePath);
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to save scene {} to {}", scene.id, filePath, e);
            return false;
        }
    }

    /**
     * Find existing file for a scene considering its pack context.
     */
    @javax.annotation.Nullable
    private static Path findExistingFileForScene(DslScene scene) {
        if (scene.pack != null && !scene.pack.isEmpty()) {
            return findExistingFile(getPackSceneDir(scene.pack), scene.id);
        }
        return findExistingFile(getSceneDir(), scene.id);
    }

    /**
     * Find the existing JSON file that contains a scene with the given id.
     */
    private static Path findExistingFile(Path dir, String sceneId) {
        if (!Files.exists(dir)) return null;
        try (Stream<Path> paths = Files.list(dir)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")).toList()) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
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
        // Search flat directory first, then all pack subdirectories
        Path existing = findExistingFile(getSceneDir(), sceneId);
        if (existing == null) {
            existing = findExistingFileInPacks(sceneId);
        }
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

    /**
     * Search all pack subdirectories for a scene with the given id.
     */
    @javax.annotation.Nullable
    private static Path findExistingFileInPacks(String sceneId) {
        Path packsDir = getSceneDir().resolve(PACKS_SUBDIR);
        if (!Files.exists(packsDir)) return null;
        try (Stream<Path> packDirs = Files.list(packsDir)) {
            for (Path packDir : packDirs.filter(Files::isDirectory).toList()) {
                Path found = findExistingFile(packDir, sceneId);
                if (found != null) return found;
            }
        } catch (IOException ignored) {}
        return null;
    }

    /**
     * Find the JSON file for a scene identified by its scene key.
     * Scene key formats:
     * - Local: "ponderer:example" (no pack prefix)
     * - Pack: "[my_pack] ponderer:example"
     */
    @javax.annotation.Nullable
    private static Path findExistingFileByKey(String sceneKey) {
        if (sceneKey == null || sceneKey.isBlank()) return null;
        String packPrefix = DslScene.extractPackPrefix(sceneKey);
        String sceneId;
        String packName;
        if (packPrefix != null) {
            sceneId = sceneKey.substring(packPrefix.length()).trim();
            packName = packPrefix.substring(1, packPrefix.length() - 1);
        } else {
            sceneId = sceneKey;
            packName = null;
        }

        // Search in the appropriate directory
        Path searchDir = packName != null ? getPackSceneDir(packName) : getSceneDir();
        Path result = findExistingFile(searchDir, sceneId);
        if (result != null) return result;

        // Fallback: search flat directory even for pack scenes
        if (packName != null) {
            return findExistingFile(getSceneDir(), sceneId);
        }
        return null;
    }

    /**
     * Delete a scene's local JSON file by its scene key.
     * Scene key formats: "ponderer:example" or "[my_pack] ponderer:example"
     */
    public static boolean deleteSceneByKey(String sceneKey) {
        if (sceneKey == null || sceneKey.isBlank()) return false;
        Path existing = findExistingFileByKey(sceneKey);
        if (existing == null) {
            LOGGER.warn("No local file found for scene key: {}", sceneKey);
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

        Path scriptsDir = getSceneDir();
        Path structureDir = getStructureDir();
        try {
            Files.createDirectories(scriptsDir);
            Files.createDirectories(structureDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create ponderer directories", e);
            return;
        }

        if (Files.exists(marker)) return;

        extractResource("data/ponderer/default_scripts/ponderer_example.json",
            scriptsDir.resolve("ponderer_example.json"));
        extractResource("data/ponderer/default_structures/ponderer_example_1.nbt",
            structureDir.resolve("ponderer_example_1.nbt"));
        extractResource("data/ponderer/default_structures/ponderer_example_2.nbt",
            structureDir.resolve("ponderer_example_2.nbt"));

        try {
            Files.writeString(marker, "initialized", StandardCharsets.UTF_8);
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

    /**
     * Try to open a built-in structure from the jar as a fallback.
     * Returns null if no built-in resource exists for the given path.
     */
    public static InputStream openBuiltinStructure(String path) {
        String resourcePath = "data/ponderer/default_structures/" + path + ".nbt";
        return SceneStore.class.getClassLoader().getResourceAsStream(resourcePath);
    }

    /**
     * Check whether a structure name would shadow a built-in structure bundled in the jar.
     */
    public static boolean isBuiltinStructureName(String name) {
        if (name == null || name.isBlank()) return false;
        String cleaned = name.trim();
        if (cleaned.toLowerCase(Locale.ROOT).endsWith(".nbt")) {
            cleaned = cleaned.substring(0, cleaned.length() - 4);
        }
        try (InputStream in = openBuiltinStructure(cleaned)) {
            return in != null;
        } catch (IOException ignored) {
            return false;
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

        // 1. Load flat files (local scenes)
        try (Stream<Path> paths = Files.list(dir)) {
            paths.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                .sorted(Comparator.comparing(Path::toString))
                .forEach(path -> loadSceneFile(path, loaded));
        } catch (IOException e) {
            LOGGER.error("Failed to list scene directory: {}", dir, e);
        }

        // 2. Load pack subdirectories (_packs/{PackName}/)
        Path packsDir = dir.resolve(PACKS_SUBDIR);
        if (Files.exists(packsDir)) {
            try (Stream<Path> packDirs = Files.list(packsDir)) {
                for (Path packDir : packDirs.filter(Files::isDirectory).sorted().toList()) {
                    try (Stream<Path> paths = Files.list(packDir)) {
                        paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                            .sorted(Comparator.comparing(Path::toString))
                            .forEach(path -> loadSceneFile(path, loaded));
                    } catch (IOException e) {
                        LOGGER.warn("Failed to list pack directory: {}", packDir, e);
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to list packs directory: {}", packsDir, e);
            }
        }

        SceneRuntime.setScenes(loaded);
        LOGGER.info("Loaded {} ponderer scene(s) from {}", loaded.size(), dir);
        return loaded.size();
    }

    private static void loadSceneFile(Path path, List<DslScene> loaded) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            DslScene scene = GSON.fromJson(reader, DslScene.class);
            if (scene == null || scene.id == null || scene.id.isBlank()) {
                LOGGER.warn("Skipping invalid scene file (missing id): {}", path);
                return;
            }
            sanitizeScene(scene);
            scene.sourceFile = path.getFileName().toString();
            loaded.add(scene);
        } catch (Exception e) {
            LOGGER.warn("Failed to read scene file: {}", path, e);
        }
    }

    /**
     * Ensure every scene segment starts with a "show_structure" step.
     * If the first meaningful step is not show_structure, prepend one.
     * This prevents crashes when operations like hide_section come first.
     */
    public static void sanitizeScene(DslScene scene) {
        if (scene.scenes != null) {
            for (DslScene.SceneSegment seg : scene.scenes) {
                ensureFirstStepIsShowStructure(seg);
            }
        }
        // Also handle legacy flat steps list
        if (scene.steps != null && !scene.steps.isEmpty()) {
            ensureFirstStepIsShowStructureFlat(scene);
        }
    }

    private static void ensureFirstStepIsShowStructure(DslScene.SceneSegment seg) {
        if (seg.steps == null || seg.steps.isEmpty()) return;
        for (DslScene.DslStep step : seg.steps) {
            if (step == null || step.type == null) continue;
            if ("show_structure".equalsIgnoreCase(step.type)) return; // already correct
            break; // first meaningful step is not show_structure
        }
        // Prepend show_structure + idle(20t)
        List<DslScene.DslStep> fixed = new ArrayList<>();
        DslScene.DslStep showStep = new DslScene.DslStep();
        showStep.type = "show_structure";
        fixed.add(showStep);
        DslScene.DslStep idleStep = new DslScene.DslStep();
        idleStep.type = "idle";
        idleStep.duration = 20;
        fixed.add(idleStep);
        fixed.addAll(seg.steps);
        seg.steps = fixed;
    }

    /**
     * Ensures every segment in flat steps mode starts with show_structure.
     * Segments are delimited by next_scene steps.
     */
    private static void ensureFirstStepIsShowStructureFlat(DslScene scene) {
        List<DslScene.DslStep> result = new ArrayList<>();
        boolean needsShowStructure = true; // start of first segment

        for (DslScene.DslStep step : scene.steps) {
            if (step == null || step.type == null) {
                result.add(step);
                continue;
            }
            if ("next_scene".equalsIgnoreCase(step.type)) {
                result.add(step);
                needsShowStructure = true; // next segment starts
                continue;
            }
            if (needsShowStructure) {
                if (!"show_structure".equalsIgnoreCase(step.type)) {
                    // Prepend show_structure + idle(20t) before this segment's first real step
                    DslScene.DslStep showStep = new DslScene.DslStep();
                    showStep.type = "show_structure";
                    result.add(showStep);
                    DslScene.DslStep idleStep = new DslScene.DslStep();
                    idleStep.type = "idle";
                    idleStep.duration = 20;
                    result.add(idleStep);
                }
                needsShowStructure = false;
            }
            result.add(step);
        }

        scene.steps = result;
    }

    // ===== Pack Export/Import Methods =====

    /**
     * Pack all scenes and structures into a Ponderer resource pack (zip).
     * File will be created at: resourcepacks/[Ponderer] {name}.zip
     * After export: reorganizes files into _packs/{name}/ and reloads.
     */
    public static boolean packScenesAndStructures(String name, String version, String author) {
        try {
            // Prepare output directory
            Path gameDir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get();
            Path resourcepacksDir = gameDir.resolve("resourcepacks");
            Files.createDirectories(resourcepacksDir);

            String filename = "[Ponderer] " + name + ".zip";
            Path outputPath = resourcepacksDir.resolve(filename);

            // Create pack.json metadata
            String packJson = createPackMetadata(name, version, author);

            // Collect all scene files (flat + pack subdirectories)
            List<Path> allScriptFiles = collectAllScriptFiles();
            Set<String> allStructureRefs = new HashSet<>();

            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputPath), StandardCharsets.UTF_8)) {
                // Write pack.mcmeta
                writeZipEntry(zos, "pack.mcmeta", "{\"pack\": {\"pack_format\": 15, \"description\": \"Ponderer scene collection\"}}");

                // Write pack.json (Ponderer metadata)
                writeZipEntry(zos, "pack.json", packJson);

                // Write scripts: update pack field and strip filename prefix
                int count = 0;
                Set<String> usedEntryNames = new HashSet<>();
                for (Path p : allScriptFiles) {
                    String cleanJson = readAndUpdatePackField(p, name);
                    if (cleanJson == null) continue;
                    collectStructureReferences(cleanJson, allStructureRefs);
                    String cleanFilename = stripPackPrefix(p.getFileName().toString());
                    cleanFilename = deduplicateFilename(cleanFilename, usedEntryNames);
                    String entryName = "data/ponderer/scripts/" + cleanFilename;
                    zos.putNextEntry(new ZipEntry(entryName));
                    zos.write(cleanJson.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                    count++;
                }

                // Write structures (strip prefix from filenames)
                count += writeStructuresToZip(zos, allStructureRefs);

                LOGGER.info("Packed {} files into {}", count, filename);
            }

            // Auto-update registry on export
            updateRegistryAfterExport(outputPath, name, version, author);

            // Reorganize files on disk: move all to _packs/{name}/ with proper naming
            reorganizeFilesForPack(name, allScriptFiles);

            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to pack Ponderer scenes and structures", e);
            return false;
        }
    }

    /**
     * Pack selected scenes and their structures into a Ponderer resource pack (zip).
     * Only includes scenes whose IDs are in the selectedSceneIds set.
     * File will be created at: resourcepacks/[Ponderer] {name}.zip
     * After export: reorganizes exported files into _packs/{name}/ and reloads.
     */
    public static boolean packSelectedScenesAndStructures(String name, String version, String author, Set<String> selectedSceneIds) {
        if (selectedSceneIds == null || selectedSceneIds.isEmpty()) {
            return packScenesAndStructures(name, version, author);
        }

        try {
            // Prepare output directory
            Path gameDir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get();
            Path resourcepacksDir = gameDir.resolve("resourcepacks");
            Files.createDirectories(resourcepacksDir);

            String filename = "[Ponderer] " + name + ".zip";
            Path outputPath = resourcepacksDir.resolve(filename);

            // Create pack.json metadata
            String packJson = createPackMetadata(name, version, author);

            Set<String> requiredStructures = new HashSet<>();
            List<Path> exportedFiles = new ArrayList<>();

            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputPath), StandardCharsets.UTF_8)) {
                // Write pack.mcmeta
                writeZipEntry(zos, "pack.mcmeta", "{\"pack\": {\"pack_format\": 15, \"description\": \"Ponderer scene collection\"}}");

                // Write pack.json (Ponderer metadata)
                writeZipEntry(zos, "pack.json", packJson);

                // Write selected scripts
                int count = 0;
                Set<String> usedEntryNames = new HashSet<>();
                List<Path> allScriptFiles = collectAllScriptFiles();
                for (Path p : allScriptFiles) {
                    // Read scene to check if it's selected
                    try {
                        String rawJson = Files.readString(p, StandardCharsets.UTF_8);
                        DslScene scene = GSON.fromJson(rawJson, DslScene.class);
                        if (scene == null || scene.id == null) continue;

                        // Check if this scene is in the selected set
                        boolean isSelected = selectedSceneIds.contains(scene.id) ||
                            selectedSceneIds.contains(scene.sceneKey());
                        if (!isSelected) {
                            // Also try matching by filename without extension
                            String fileBase = p.getFileName().toString();
                            if (fileBase.endsWith(".json")) fileBase = fileBase.substring(0, fileBase.length() - 5);
                            String stripped = stripPackPrefix(fileBase);
                            isSelected = selectedSceneIds.stream()
                                .anyMatch(id -> id.equals(stripped) || id.endsWith(":" + stripped));
                        }

                        if (isSelected) {
                            // Update pack field and write to zip
                            scene.pack = name;
                            String cleanJson = GSON_PRETTY.toJson(scene);
                            collectStructureReferences(cleanJson, requiredStructures);
                            String cleanFilename = stripPackPrefix(p.getFileName().toString());
                            cleanFilename = deduplicateFilename(cleanFilename, usedEntryNames);
                            String entryName = "data/ponderer/scripts/" + cleanFilename;
                            zos.putNextEntry(new ZipEntry(entryName));
                            zos.write(cleanJson.getBytes(StandardCharsets.UTF_8));
                            zos.closeEntry();
                            exportedFiles.add(p);
                            count++;
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Failed to process scene file: {}", p, e);
                    }
                }

                // Write only referenced structures (strip prefix)
                count += writeStructuresToZip(zos, requiredStructures);

                LOGGER.info("Packed {} files into {} (selected {} scenes)", count, filename, selectedSceneIds.size());
            }

            // Auto-update registry on export
            updateRegistryAfterExport(outputPath, name, version, author);

            // Reorganize exported files on disk
            reorganizeFilesForPack(name, exportedFiles);

            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to pack selected Ponderer scenes and structures", e);
            return false;
        }
    }

    /**
     * Collect all script files from flat directory and pack subdirectories.
     */
    private static List<Path> collectAllScriptFiles() {
        List<Path> result = new ArrayList<>();
        Path scriptsDir = getSceneDir();
        if (!Files.exists(scriptsDir)) return result;

        // Flat files
        try (Stream<Path> paths = Files.list(scriptsDir)) {
            paths.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                .forEach(result::add);
        } catch (IOException ignored) {}

        // Pack subdirectories
        Path packsDir = scriptsDir.resolve(PACKS_SUBDIR);
        if (Files.exists(packsDir)) {
            try (Stream<Path> packDirs = Files.list(packsDir)) {
                for (Path packDir : packDirs.filter(Files::isDirectory).toList()) {
                    try (Stream<Path> paths = Files.list(packDir)) {
                        paths.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                            .forEach(result::add);
                    } catch (IOException ignored) {}
                }
            } catch (IOException ignored) {}
        }

        return result;
    }

    /**
     * Read a scene JSON file and update its pack field.
     */
    @javax.annotation.Nullable
    private static String readAndUpdatePackField(Path path, String packName) {
        try {
            String rawJson = Files.readString(path, StandardCharsets.UTF_8);
            DslScene scene = GSON.fromJson(rawJson, DslScene.class);
            if (scene == null) return null;
            scene.pack = packName;
            return GSON_PRETTY.toJson(scene);
        } catch (Exception e) {
            LOGGER.warn("Failed to read/update scene file: {}", path, e);
            return null;
        }
    }

    /**
     * Strip [PackName] prefix from a filename.
     * "[MyPack] oak_log.json" → "oak_log.json"
     * "oak_log.json" → "oak_log.json"
     */
    private static String stripPackPrefix(String filename) {
        String prefix = DslScene.extractPackPrefix(filename);
        if (prefix != null) {
            return filename.substring(prefix.length()).trim();
        }
        return filename;
    }

    /**
     * Ensure a filename is unique within the given set. If it already exists,
     * append _1, _2, etc. before the extension until unique.
     * The unique name is added to the set before returning.
     */
    private static String deduplicateFilename(String filename, Set<String> usedNames) {
        if (usedNames.add(filename)) {
            return filename;
        }
        String base = filename;
        String ext = "";
        int dot = filename.lastIndexOf('.');
        if (dot >= 0) {
            base = filename.substring(0, dot);
            ext = filename.substring(dot);
        }
        int suffix = 1;
        String candidate;
        do {
            suffix++;
            candidate = base + "_" + suffix + ext;
        } while (!usedNames.add(candidate));
        return candidate;
    }

    /**
     * Write structure files to zip, stripping pack prefix from filenames.
     * Returns the number of files written.
     */
    private static int writeStructuresToZip(ZipOutputStream zos, Set<String> structureRefs) throws IOException {
        int count = 0;
        Path structuresDir = getStructureDir();
        if (!Files.exists(structuresDir)) return 0;

        // Collect all structure files (flat + pack subdirectories)
        List<Path> allStructureFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.list(structuresDir)) {
            paths.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".nbt"))
                .forEach(allStructureFiles::add);
        } catch (IOException ignored) {}

        Path packsDir = structuresDir.resolve(PACKS_SUBDIR);
        if (Files.exists(packsDir)) {
            try (Stream<Path> packDirs = Files.list(packsDir)) {
                for (Path packDir : packDirs.filter(Files::isDirectory).toList()) {
                    try (Stream<Path> paths = Files.list(packDir)) {
                        paths.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".nbt"))
                            .forEach(allStructureFiles::add);
                    } catch (IOException ignored) {}
                }
            } catch (IOException ignored) {}
        }

        Set<String> writtenEntries = new HashSet<>();
        for (Path p : allStructureFiles) {
            String fileName = p.getFileName().toString();
            String cleanFileName = stripPackPrefix(fileName);
            String baseName = cleanFileName.endsWith(".nbt") ? cleanFileName.substring(0, cleanFileName.length() - 4) : cleanFileName;

            // Check if this structure is referenced
            boolean isReferenced = structureRefs.stream()
                .anyMatch(ref -> {
                    String cleanRef = ref.replace(".nbt", "");
                    return cleanRef.equals(baseName) || cleanRef.endsWith(":" + baseName);
                });

            if (isReferenced) {
                String entryName = "data/ponderer/structures/" + cleanFileName;
                if (writtenEntries.add(entryName)) { // avoid duplicates
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(p, zos);
                    zos.closeEntry();
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * After export, reorganize files on disk:
     * Move exported files into _packs/{packName}/ with proper [PackName] prefix and pack field.
     * Then reload scripts.
     */
    private static void reorganizeFilesForPack(String packName, List<Path> exportedFiles) {
        Path packScriptsDir = getPackSceneDir(packName);
        try {
            Files.createDirectories(packScriptsDir);
        } catch (IOException e) {
            LOGGER.warn("Failed to create pack directory: {}", packScriptsDir, e);
            return;
        }

        for (Path sourcePath : exportedFiles) {
            try {
                // Read and update pack field
                String rawJson = Files.readString(sourcePath, StandardCharsets.UTF_8);
                DslScene scene = GSON.fromJson(rawJson, DslScene.class);
                if (scene == null) continue;
                scene.pack = packName;
                String updatedJson = GSON_PRETTY.toJson(scene);

                // Determine target filename: [PackName] clean_name.json
                String cleanFilename = stripPackPrefix(sourcePath.getFileName().toString());
                String prefixedFilename = "[" + packName + "] " + cleanFilename;
                Path targetPath = packScriptsDir.resolve(prefixedFilename);

                // Write to new location
                Files.writeString(targetPath, updatedJson, StandardCharsets.UTF_8);

                // Delete original if it's in a different location
                if (!sourcePath.equals(targetPath)) {
                    Files.deleteIfExists(sourcePath);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to reorganize file: {}", sourcePath, e);
            }
        }

        // Also reorganize referenced structures
        reorganizeStructuresForPack(packName);

        LOGGER.info("Reorganized files for pack: {}", packName);
    }

    /**
     * Move structure files referenced by pack scenes into the pack structure directory.
     */
    private static void reorganizeStructuresForPack(String packName) {
        Path packStructuresDir = getPackStructureDir(packName);
        Path packScriptsDir = getPackSceneDir(packName);

        // Collect structure references from pack scripts
        Set<String> structureRefs = new HashSet<>();
        if (Files.exists(packScriptsDir)) {
            try (Stream<Path> paths = Files.list(packScriptsDir)) {
                for (Path p : paths.filter(f -> f.getFileName().toString().endsWith(".json")).toList()) {
                    try {
                        String json = Files.readString(p, StandardCharsets.UTF_8);
                        collectStructureReferences(json, structureRefs);
                    } catch (Exception ignored) {}
                }
            } catch (IOException ignored) {}
        }
        if (structureRefs.isEmpty()) return;

        try {
            Files.createDirectories(packStructuresDir);
        } catch (IOException e) {
            LOGGER.warn("Failed to create pack structure directory: {}", packStructuresDir, e);
            return;
        }

        // Move matching structures from flat directory to pack directory
        Path structuresDir = getStructureDir();
        if (Files.exists(structuresDir)) {
            try (Stream<Path> paths = Files.list(structuresDir)) {
                for (Path p : paths.filter(f -> Files.isRegularFile(f) && f.getFileName().toString().endsWith(".nbt")).toList()) {
                    String fileName = p.getFileName().toString();
                    String baseName = fileName.endsWith(".nbt") ? fileName.substring(0, fileName.length() - 4) : fileName;

                    boolean isReferenced = structureRefs.stream()
                        .anyMatch(ref -> {
                            String cleanRef = ref.replace(".nbt", "");
                            return cleanRef.equals(baseName) || cleanRef.endsWith(":" + baseName);
                        });

                    if (isReferenced) {
                        String prefixedName = "[" + packName + "] " + fileName;
                        Path targetPath = packStructuresDir.resolve(prefixedName);
                        if (!Files.exists(targetPath)) {
                            Files.copy(p, targetPath);
                        }
                        // Don't delete from flat dir — other scenes might still reference it
                    }
                }
            } catch (IOException ignored) {}
        }
    }

    private static void collectStructureReferences(String sceneJson, Set<String> structures) {
        try {
            // Simple JSON parsing to find structure references
            // Look for "structure": "xxx" patterns
            int idx = 0;
            String searchFor = "\"structure\"";
            while ((idx = sceneJson.indexOf(searchFor, idx)) != -1) {
                idx += searchFor.length();
                // Find the colon
                int colonIdx = sceneJson.indexOf(":", idx);
                if (colonIdx != -1) {
                    // Find the quoted value
                    int quoteStart = sceneJson.indexOf("\"", colonIdx);
                    if (quoteStart != -1) {
                        int quoteEnd = sceneJson.indexOf("\"", quoteStart + 1);
                        if (quoteEnd != -1) {
                            String structRef = sceneJson.substring(quoteStart + 1, quoteEnd);
                            if (!structRef.isEmpty() && !structRef.matches("\\d+")) {
                                structures.add(structRef);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to collect structure references from scene", e);
        }
    }

    private static String createPackMetadata(String name, String version, String author) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"pack\": {\n");
        sb.append("    \"pack_format\": 15,\n");
        sb.append("    \"description\": \"Ponderer scene collection\"\n");
        sb.append("  },\n");
        sb.append("  \"ponderer\": {\n");
        sb.append("    \"name\": \"").append(escapeJson(name)).append("\",\n");
        sb.append("    \"version\": \"").append(escapeJson(version)).append("\",\n");
        sb.append("    \"author\": \"").append(escapeJson(author)).append("\",\n");
        sb.append("    \"description\": \"Ponderer scene pack\"\n");
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * After exporting a pack, update the registry so that the version and hash are tracked.
     * This prevents re-importing the same pack on next startup.
     */
    private static void updateRegistryAfterExport(Path zipPath, String name, String version, String author) {
        try {
            PonderPackInfo info = PonderPackInfo.fromZip(zipPath);
            if (info == null) return;
            String displayName = PonderPackRegistry.getDisplayName(name);
            String fileHash = computeSha256(zipPath);
            PonderPackRegistry.addOrUpdatePack(displayName, info, fileHash);
            LOGGER.info("Updated registry after export: {} v{}", displayName, version);
        } catch (Exception e) {
            LOGGER.warn("Failed to update registry after export", e);
        }
    }

    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    private static void writeZipEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    /**
     * Load a Ponderer pack from resourcepacks directory.
     * Extracts scenes and structures into _packs/{PackName}/ subdirectories.
     *
     * Logic:
     * - Same version → skip (already loaded)
     * - Different version (higher or lower) → backup user-modified files as .bak, then overwrite
     * - forceOverwrite → always extract (no version check)
     *
     * @return PackUpdateInfo if the pack was updated, null if skipped
     */
    @javax.annotation.Nullable
    public static PackUpdateInfo loadPonderPackFromResourcePack(Path zipPath, boolean forceOverwrite) throws IOException {
        if (!Files.exists(zipPath)) {
            LOGGER.warn("Pack file not found: {}", zipPath);
            return null;
        }

        // Read pack info
        PonderPackInfo info = PonderPackInfo.fromZip(zipPath);
        if (info == null) {
            LOGGER.warn("Invalid Ponderer pack: {}", zipPath);
            return null;
        }

        String displayName = PonderPackRegistry.getDisplayName(info.name);
        String newFileHash = computeSha256(zipPath);
        String oldVersion = null;
        long loadedAtMillis = 0;

        PonderPackRegistry.PackEntry existing = PonderPackRegistry.getPack(displayName);
        if (existing != null) {
            oldVersion = existing.version;
            // Parse loadedAt timestamp to epoch millis for file modification comparison
            loadedAtMillis = parseLoadedAtMillis(existing.loadedAt);

            if (!forceOverwrite && existing.version.equals(info.version)) {
                // Same version → skip
                LOGGER.info("Pack {} already loaded (v{}), skipping", info.name, existing.version);
                return null;
            }
            // Version differs → extract with backup
            LOGGER.info("Pack {} updating: v{} -> v{}", info.name, existing.version, info.version);
        }

        // Extract pack (with backup for user-modified files)
        int[] result = extractPonderPackWithBackup(zipPath, info, loadedAtMillis);
        int count = result[0];
        int conflicts = result[1];

        // Update registry
        PonderPackRegistry.addOrUpdatePack(displayName, info, newFileHash);

        LOGGER.info("Loaded Ponderer pack: {} ({} files, {} conflicts)", info.name, count, conflicts);
        return new PackUpdateInfo(info.name, oldVersion, info.version, count, conflicts);
    }

    /**
     * Parse the ISO date-time string from registry into epoch millis.
     */
    private static long parseLoadedAtMillis(@javax.annotation.Nullable String loadedAt) {
        if (loadedAt == null || loadedAt.isEmpty()) return 0;
        try {
            LocalDateTime ldt = LocalDateTime.parse(loadedAt, DateTimeFormatter.ISO_DATE_TIME);
            return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            LOGGER.warn("Failed to parse loadedAt timestamp: {}", loadedAt, e);
            return 0;
        }
    }

    /**
     * Extract pack contents into _packs/{PackName}/ subdirectories.
     * Scripts get [PackName] prefix in filename and "pack" field injected into JSON.
     * Structures get [PackName] prefix in filename.
     * Only backs up files that were modified by the user after loadedAtMillis.
     *
     * @return int[2]: [0] = total files extracted, [1] = conflict count (user-modified files backed up)
     */
    private static int[] extractPonderPackWithBackup(Path zipPath, PonderPackInfo info, long loadedAtMillis) throws IOException {
        int count = 0;
        int conflicts = 0;
        Path packScriptsDir = getPackSceneDir(info.name);
        Path packStructuresDir = getPackStructureDir(info.name);

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                if (name.startsWith("data/ponderer/scripts/")) {
                    String fileName = name.substring("data/ponderer/scripts/".length());
                    if (fileName.isEmpty() || fileName.endsWith("/")) continue;
                    String prefixedName = info.packPrefix + " " + fileName;
                    Path targetPath = packScriptsDir.resolve(prefixedName);

                    Files.createDirectories(targetPath.getParent());
                    if (backupIfModified(targetPath, loadedAtMillis)) {
                        conflicts++;
                    }

                    // Read JSON, inject pack field, write
                    byte[] rawBytes = zis.readAllBytes();
                    String json = new String(rawBytes, StandardCharsets.UTF_8);
                    json = injectPackField(json, info.name);
                    Files.writeString(targetPath, json, StandardCharsets.UTF_8);
                    count++;
                } else if (name.startsWith("data/ponderer/structures/")) {
                    String fileName = name.substring("data/ponderer/structures/".length());
                    if (fileName.isEmpty() || fileName.endsWith("/")) continue;
                    String prefixedName = info.packPrefix + " " + fileName;
                    Path targetPath = packStructuresDir.resolve(prefixedName);

                    Files.createDirectories(targetPath.getParent());
                    if (backupIfModified(targetPath, loadedAtMillis)) {
                        conflicts++;
                    }
                    Files.write(targetPath, zis.readAllBytes());
                    count++;
                }
            }
        }

        return new int[]{count, conflicts};
    }

    /**
     * Inject "pack" field into scene JSON string.
     */
    private static String injectPackField(String json, String packName) {
        try {
            DslScene scene = GSON.fromJson(json, DslScene.class);
            if (scene != null) {
                scene.pack = packName;
                return GSON_PRETTY.toJson(scene);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to inject pack field, writing raw content", e);
        }
        return json;
    }

    /**
     * If the target file exists and was modified by the user (after loadedAt timestamp),
     * back it up as .bak before it gets overwritten.
     *
     * @param targetPath the file to check
     * @param loadedAtMillis the epoch millis when the pack was last loaded (0 = always backup if exists)
     * @return true if a backup was made (file was user-modified), false otherwise
     */
    private static boolean backupIfModified(Path targetPath, long loadedAtMillis) {
        if (!Files.exists(targetPath)) return false;
        try {
            long fileModified = Files.getLastModifiedTime(targetPath).toMillis();
            // Only backup if the file was modified after the last load time
            // (meaning the user hand-edited it). Allow 5 second tolerance.
            if (loadedAtMillis > 0 && fileModified <= loadedAtMillis + 5000) {
                return false; // File was not modified by user, just overwrite
            }
            Path bakPath = targetPath.resolveSibling(targetPath.getFileName().toString() + ".bak");
            Files.copy(targetPath, bakPath, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Backed up user-modified file: {} -> {}", targetPath.getFileName(), bakPath.getFileName());
            return true;
        } catch (IOException e) {
            LOGGER.warn("Failed to backup file: {}", targetPath, e);
            return false;
        }
    }

    /**
     * Check if at least one extracted script file from a pack still exists on disk.
     */
    private static boolean packScriptFilesExist(String packName) {
        // Check _packs/{packName}/ subdirectory
        Path packDir = getPackSceneDir(packName);
        if (Files.exists(packDir)) {
            try (Stream<Path> paths = Files.list(packDir)) {
                if (paths.anyMatch(p -> p.getFileName().toString().endsWith(".json"))) {
                    return true;
                }
            } catch (IOException ignored) {}
        }
        return false;
    }

    private static String computeSha256(Path file) {
        try {
            byte[] data = Files.readAllBytes(file);
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);

            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            LOGGER.warn("Failed to compute SHA-256 for {}", file, e);
            return "";
        }
    }

    /** Result of auto-loading packs at startup. */
    public static class AutoLoadResult {
        public final List<String> orphanedPacks;
        public final List<PackUpdateInfo> updatedPacks;

        public AutoLoadResult(List<String> orphanedPacks, List<PackUpdateInfo> updatedPacks) {
            this.orphanedPacks = orphanedPacks;
            this.updatedPacks = updatedPacks;
        }
    }

    /**
     * Auto-load Ponderer packs from resourcepacks directory.
     * Called during client setup to load packs on first launch or when new packs are added.
     *
     * @return AutoLoadResult with orphaned pack names and updated pack info
     */
    public static AutoLoadResult autoLoadPonderPacks() {
        PonderPackRegistry.load();

        Path gameDir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get();
        Path resourcepacksDir = gameDir.resolve("resourcepacks");

        List<PackUpdateInfo> updates = new ArrayList<>();

        if (Files.exists(resourcepacksDir)) {
            try (Stream<Path> paths = Files.list(resourcepacksDir)) {
                for (Path p : paths.filter(path -> path.toString().toLowerCase().endsWith(".zip")).toList()) {
                    try {
                        PackUpdateInfo updateInfo = loadPonderPackFromResourcePack(p, false);
                        if (updateInfo != null) {
                            updates.add(updateInfo);
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Failed to load pack: {}", p, e);
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to scan resourcepacks directory", e);
            }
        }

        // Detect orphaned packs: registered in registry but all scripts deleted
        List<String> orphaned = new ArrayList<>();
        for (var entry : PonderPackRegistry.getAllPacks().entrySet()) {
            String displayName = entry.getKey();
            PonderPackRegistry.PackEntry pack = entry.getValue();
            // Extract pack name from packPrefix "[PackName]" or use name field
            String packName = pack.name;
            if (packName == null && pack.packPrefix != null && pack.packPrefix.startsWith("[") && pack.packPrefix.endsWith("]")) {
                packName = pack.packPrefix.substring(1, pack.packPrefix.length() - 1);
            }
            if (packName != null && !packScriptFilesExist(packName)) {
                orphaned.add(displayName);
                LOGGER.info("Orphaned pack detected: {} (no script files found for pack {})", displayName, packName);
            }
        }
        return new AutoLoadResult(orphaned, updates);
    }

    /**
     * Remove orphaned packs from registry (unregister without deleting zip).
     */
    public static void removeOrphanedPacks(List<String> displayNames) {
        for (String name : displayNames) {
            PonderPackRegistry.removePack(name);
            LOGGER.info("Removed orphaned pack from registry: {}", name);
        }
    }
}