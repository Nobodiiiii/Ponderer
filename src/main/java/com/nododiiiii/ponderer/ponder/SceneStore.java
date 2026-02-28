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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class SceneStore {
    private static final Logger LOGGER = LogUtils.getLogger();
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

    /**
     * Find the JSON file for a scene identified by its scene key.
     * Scene key formats:
     * - Local: "ponderer:example" (no pack prefix)
     * - Pack: "[my_pack] ponderer:example"
     */
    @javax.annotation.Nullable
    private static Path findExistingFileByKey(Path dir, String sceneKey) {
        if (!Files.exists(dir)) return null;
        String packPrefix = DslScene.extractPackPrefix(sceneKey);
        String sceneId;
        if (packPrefix != null) {
            // "[my_pack] ponderer:example" → sceneId = "ponderer:example"
            sceneId = sceneKey.substring(packPrefix.length()).trim();
        } else {
            sceneId = sceneKey;
        }

        try (Stream<Path> paths = Files.list(dir)) {
            Path fallback = null;
            for (Path path : paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")).toList()) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    DslScene existing = GSON.fromJson(reader, DslScene.class);
                    if (existing == null || !sceneId.equals(existing.id)) continue;
                    String filePrefix = DslScene.extractPackPrefix(path.getFileName().toString());
                    if (packPrefix == null && filePrefix == null) {
                        return path; // local scene matches local file
                    }
                    if (packPrefix != null && packPrefix.equals(filePrefix)) {
                        return path; // pack prefix matches
                    }
                    if (fallback == null) {
                        fallback = path; // keep first match as fallback
                    }
                } catch (Exception ignored) {
                }
            }
            return fallback;
        } catch (IOException ignored) {
        }
        return null;
    }

    /**
     * Delete a scene's local JSON file by its scene key.
     * Scene key formats: "ponderer:example" or "[my_pack] ponderer:example"
     */
    public static boolean deleteSceneByKey(String sceneKey) {
        if (sceneKey == null || sceneKey.isBlank()) return false;
        Path dir = getSceneDir();
        Path existing = findExistingFileByKey(dir, sceneKey);
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

        try (Stream<Path> paths = Files.list(dir)) {
            paths.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                .sorted(Comparator.comparing(Path::toString))
                .forEach(path -> {
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
                });
        } catch (IOException e) {
            LOGGER.error("Failed to list scene directory: {}", dir, e);
        }

        SceneRuntime.setScenes(loaded);
        LOGGER.info("Loaded {} ponderer scene(s) from {}", loaded.size(), dir);
        return loaded.size();
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

            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputPath), StandardCharsets.UTF_8)) {
                // Write pack.mcmeta
                writeZipEntry(zos, "pack.mcmeta", "{\"pack\": {\"pack_format\": 15, \"description\": \"Ponderer scene collection\"}}");

                // Write pack.json (Ponderer metadata)
                writeZipEntry(zos, "pack.json", packJson);

                // Write scripts
                int count = 0;
                Path scriptsDir = getSceneDir();
                if (Files.exists(scriptsDir)) {
                    try (Stream<Path> paths = Files.walk(scriptsDir)) {
                        for (Path p : paths.filter(Files::isRegularFile).toList()) {
                            String entryName = "data/ponderer/scripts/" + scriptsDir.relativize(p).toString().replace("\\", "/");
                            zos.putNextEntry(new ZipEntry(entryName));
                            Files.copy(p, zos);
                            zos.closeEntry();
                            count++;
                        }
                    }
                }

                // Write structures
                Path structuresDir = getStructureDir();
                if (Files.exists(structuresDir)) {
                    try (Stream<Path> paths = Files.walk(structuresDir)) {
                        for (Path p : paths.filter(Files::isRegularFile).toList()) {
                            String entryName = "data/ponderer/structures/" + structuresDir.relativize(p).toString().replace("\\", "/");
                            zos.putNextEntry(new ZipEntry(entryName));
                            Files.copy(p, zos);
                            zos.closeEntry();
                            count++;
                        }
                    }
                }

                LOGGER.info("Packed {} files into {}", count, filename);
            }

            // Auto-update registry on export
            updateRegistryAfterExport(outputPath, name, version, author);

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

            Set<String> requiredStructures = new java.util.HashSet<>();

            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputPath), StandardCharsets.UTF_8)) {
                // Write pack.mcmeta
                writeZipEntry(zos, "pack.mcmeta", "{\"pack\": {\"pack_format\": 15, \"description\": \"Ponderer scene collection\"}}");

                // Write pack.json (Ponderer metadata)
                writeZipEntry(zos, "pack.json", packJson);

                // Write selected scripts and collect referenced structures
                int count = 0;
                Path scriptsDir = getSceneDir();
                if (Files.exists(scriptsDir)) {
                    try (Stream<Path> paths = Files.walk(scriptsDir)) {
                        for (Path p : paths.filter(Files::isRegularFile).toList()) {
                            String fileName = p.getFileName().toString();
                            // Extract scene ID from filename (remove .json extension)
                            String sceneIdFromFile = fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;

                            // Check if this scene is in the selected set
                            boolean isSelected = selectedSceneIds.stream()
                                .anyMatch(id -> id.equals(sceneIdFromFile) || id.endsWith(":" + sceneIdFromFile));

                            if (isSelected) {
                                // Read the scene file and extract structure references
                                try {
                                    String sceneJson = Files.readString(p, StandardCharsets.UTF_8);
                                    collectStructureReferences(sceneJson, requiredStructures);
                                } catch (Exception ignored) {}

                                String entryName = "data/ponderer/scripts/" + scriptsDir.relativize(p).toString().replace("\\", "/");
                                zos.putNextEntry(new ZipEntry(entryName));
                                Files.copy(p, zos);
                                zos.closeEntry();
                                count++;
                            }
                        }
                    }
                }

                // Write only referenced structures
                Path structuresDir = getStructureDir();
                if (Files.exists(structuresDir)) {
                    try (Stream<Path> paths = Files.walk(structuresDir)) {
                        for (Path p : paths.filter(Files::isRegularFile).toList()) {
                            String fileName = p.getFileName().toString();
                            // Check if this structure is referenced
                            boolean isReferenced = requiredStructures.stream()
                                .anyMatch(ref -> {
                                    String cleanRef = ref.replace(".nbt", "");
                                    String cleanFileName = fileName.endsWith(".nbt") ? fileName.substring(0, fileName.length() - 4) : fileName;
                                    return cleanRef.equals(cleanFileName) || cleanRef.endsWith(":" + cleanFileName);
                                });

                            if (isReferenced) {
                                String entryName = "data/ponderer/structures/" + structuresDir.relativize(p).toString().replace("\\", "/");
                                zos.putNextEntry(new ZipEntry(entryName));
                                Files.copy(p, zos);
                                zos.closeEntry();
                                count++;
                            }
                        }
                    }
                }

                LOGGER.info("Packed {} files into {} (selected {} scenes)", count, filename, selectedSceneIds.size());
            }

            // Auto-update registry on export
            updateRegistryAfterExport(outputPath, name, version, author);

            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to pack selected Ponderer scenes and structures", e);
            return false;
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
     * Extracts scenes and structures with [PackName] prefix.
     *
     * Logic:
     * - Same version → skip (already loaded)
     * - Different version (higher or lower) → backup modified local files as .bak, then overwrite
     * - forceOverwrite → always extract (no version check)
     */
    public static int loadPonderPackFromResourcePack(Path zipPath, boolean forceOverwrite) throws IOException {
        if (!Files.exists(zipPath)) {
            LOGGER.warn("Pack file not found: {}", zipPath);
            return 0;
        }

        // Read pack info
        PonderPackInfo info = PonderPackInfo.fromZip(zipPath);
        if (info == null) {
            LOGGER.warn("Invalid Ponderer pack: {}", zipPath);
            return 0;
        }

        String displayName = PonderPackRegistry.getDisplayName(info.name);
        String newFileHash = computeSha256(zipPath);

        if (!forceOverwrite) {
            PonderPackRegistry.PackEntry existing = PonderPackRegistry.getPack(displayName);
            if (existing != null) {
                if (existing.version.equals(info.version)) {
                    // Same version → skip
                    LOGGER.info("Pack {} already loaded (v{}), skipping", info.name, existing.version);
                    return 0;
                }
                // Version differs → extract with backup
                LOGGER.info("Pack {} updating: v{} -> v{}", info.name, existing.version, info.version);
            }
        }

        // Extract pack (with backup for modified local files)
        int count = extractPonderPackWithBackup(zipPath, info);

        // Update registry
        PonderPackRegistry.addOrUpdatePack(displayName, info, newFileHash);

        LOGGER.info("Loaded Ponderer pack: {} ({})", info.name, count + " files");
        return count;
    }

    /**
     * Extract pack contents, backing up any locally modified files as .bak before overwriting.
     */
    private static int extractPonderPackWithBackup(Path zipPath, PonderPackInfo info) throws IOException {
        int count = 0;
        Path scriptsDir = getSceneDir();
        Path structuresDir = getStructureDir();

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                if (name.startsWith("data/ponderer/scripts/")) {
                    String fileName = name.substring("data/ponderer/scripts/".length());
                    String prefixedName = info.packPrefix + " " + fileName;
                    Path targetPath = scriptsDir.resolve(prefixedName);

                    Files.createDirectories(targetPath.getParent());
                    backupIfModified(targetPath);
                    Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    count++;
                } else if (name.startsWith("data/ponderer/structures/")) {
                    String fileName = name.substring("data/ponderer/structures/".length());
                    String prefixedName = info.packPrefix + " " + fileName;
                    Path targetPath = structuresDir.resolve(prefixedName);

                    Files.createDirectories(targetPath.getParent());
                    backupIfModified(targetPath);
                    Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * If the target file exists, back it up as .bak before it gets overwritten.
     */
    private static void backupIfModified(Path targetPath) {
        if (!Files.exists(targetPath)) return;
        Path bakPath = targetPath.resolveSibling(targetPath.getFileName().toString() + ".bak");
        try {
            Files.copy(targetPath, bakPath, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Backed up modified file: {} -> {}", targetPath.getFileName(), bakPath.getFileName());
        } catch (IOException e) {
            LOGGER.warn("Failed to backup file: {}", targetPath, e);
        }
    }

    /**
     * Check if at least one extracted script file from a pack prefix still exists on disk.
     */
    private static boolean packScriptFilesExist(String packPrefix) {
        Path scriptsDir = getSceneDir();
        String prefix = packPrefix + " ";
        try (Stream<Path> paths = Files.list(scriptsDir)) {
            return paths.anyMatch(p -> p.getFileName().toString().startsWith(prefix));
        } catch (IOException e) {
            return false;
        }
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

    /**
     * Auto-load Ponderer packs from resourcepacks directory.
     * Called during client setup to load packs on first launch or when new packs are added.
     *
     * @return list of orphaned pack display names (registered but all scripts deleted)
     */
    public static List<String> autoLoadPonderPacks() {
        PonderPackRegistry.load();

        Path gameDir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get();
        Path resourcepacksDir = gameDir.resolve("resourcepacks");

        if (Files.exists(resourcepacksDir)) {
            try (Stream<Path> paths = Files.list(resourcepacksDir)) {
                for (Path p : paths.filter(path -> path.toString().toLowerCase().endsWith(".zip")).toList()) {
                    try {
                        loadPonderPackFromResourcePack(p, false);
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
            if (pack.packPrefix != null && !packScriptFilesExist(pack.packPrefix)) {
                orphaned.add(displayName);
                LOGGER.info("Orphaned pack detected: {} (no script files found for prefix {})", displayName, pack.packPrefix);
            }
        }
        return orphaned;
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