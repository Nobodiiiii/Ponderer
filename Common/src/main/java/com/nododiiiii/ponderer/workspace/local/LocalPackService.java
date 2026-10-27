package com.nododiiiii.ponderer.workspace.local;

import com.nododiiiii.ponderer.workspace.api.PonderPackInfo;
import com.nododiiiii.ponderer.workspace.api.PackState;
import com.nododiiiii.ponderer.workspace.api.PackRegistry;
import com.nododiiiii.ponderer.scene.SceneCodec;
import com.nododiiiii.ponderer.scene.StructureReferences;
import com.nododiiiii.ponderer.scene.DslScene;
import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.util.SafePaths;
import org.slf4j.Logger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
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
import com.nododiiiii.ponderer.workspace.api.*;
import com.nododiiiii.ponderer.workspace.api.WorkspaceResults.*;

/** Internal file implementation. Construct through WorkspaceFiles. */
public final class LocalPackService implements PackService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BASE_DIR = "ponderer";
    private static final String SCRIPT_DIR = "scripts";
    private static final String STRUCTURE_DIR = "structures";
    private static final String PACKS_SUBDIR = "_packs";
    private static final String SAVE_ERROR_KEY_PREFIX = "ponderer.ui.save_error.";
    private final WorkspaceFiles workspace;
    LocalPackService(WorkspaceFiles workspace) { this.workspace = workspace; }

    public PackExportResult packScenesAndStructuresDetailed(String name, String version, String author) {
        PackExportResult nameFailure = validatePackExportName(name);
        if (nameFailure != null) {
            LOGGER.warn(nameFailure.englishMessage());
            return nameFailure;
        }

        String normalizedName = name.trim();
        Path resourcepacksDir = workspace.gameDir.resolve("resourcepacks");
        Path outputPath = resolvePackExportOutputPath(resourcepacksDir, normalizedName);
        if (outputPath == null) {
            PackExportResult failure = PackExportResult.failure(
                "Cannot export pack '" + normalizedName + "' because the output path could not be resolved safely",
                SAVE_ERROR_KEY_PREFIX + "pack_name_invalid"
            );
            LOGGER.warn(failure.englishMessage());
            return failure;
        }

        try {
            Files.createDirectories(resourcepacksDir);

            String filename = outputPath.getFileName() != null ? outputPath.getFileName().toString() : "[Ponderer] " + normalizedName + ".zip";
            String packJson = createPackMetadata(normalizedName, version, author);

            List<Path> allScriptFiles = collectAllScriptFiles();
            Set<String> allStructureRefs = new HashSet<>();

            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputPath), StandardCharsets.UTF_8)) {
                writeZipEntry(zos, "pack.mcmeta", "{\"pack\": {\"pack_format\": 15, \"description\": \"Ponderer scene collection\"}}");
                writeZipEntry(zos, "pack.json", packJson);

                int count = 0;
                Set<String> usedEntryNames = new HashSet<>();
                for (Path p : allScriptFiles) {
                    String cleanJson = readAndUpdatePackField(p, normalizedName);
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

                count += writeStructuresToZip(zos, allStructureRefs);
                LOGGER.info("Packed {} files into {}", count, filename);
            }

            return PackExportResult.success(outputPath, "Exported pack '" + normalizedName + "' to " + outputPath);
        } catch (IOException e) {
            PackExportResult failure = mapPackExportIoFailure(normalizedName, outputPath, e);
            LOGGER.error(failure.englishMessage(), e);
            return failure;
        }
    }

    public boolean packScenesAndStructures(String name, String version, String author) {
        return packScenesAndStructuresDetailed(name, version, author).isSuccess();
    }

    public PackExportResult packSelectedScenesAndStructuresDetailed(String name, String version, String author, Set<String> selectedSceneIds) {
        if (selectedSceneIds == null || selectedSceneIds.isEmpty()) {
            return packScenesAndStructuresDetailed(name, version, author);
        }

        PackExportResult nameFailure = validatePackExportName(name);
        if (nameFailure != null) {
            LOGGER.warn(nameFailure.englishMessage());
            return nameFailure;
        }

        String normalizedName = name.trim();
        Path resourcepacksDir = workspace.gameDir.resolve("resourcepacks");
        Path outputPath = resolvePackExportOutputPath(resourcepacksDir, normalizedName);
        if (outputPath == null) {
            PackExportResult failure = PackExportResult.failure(
                "Cannot export pack '" + normalizedName + "' because the output path could not be resolved safely",
                SAVE_ERROR_KEY_PREFIX + "pack_name_invalid"
            );
            LOGGER.warn(failure.englishMessage());
            return failure;
        }

        try {
            Files.createDirectories(resourcepacksDir);
            String filename = outputPath.getFileName() != null ? outputPath.getFileName().toString() : "[Ponderer] " + normalizedName + ".zip";
            String packJson = createPackMetadata(normalizedName, version, author);

            Set<String> requiredStructures = new HashSet<>();
            List<Path> exportedFiles = new ArrayList<>();

            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputPath), StandardCharsets.UTF_8)) {
                writeZipEntry(zos, "pack.mcmeta", "{\"pack\": {\"pack_format\": 15, \"description\": \"Ponderer scene collection\"}}");
                writeZipEntry(zos, "pack.json", packJson);

                int count = 0;
                Set<String> usedEntryNames = new HashSet<>();
                List<Path> allScriptFiles = collectAllScriptFiles();
                for (Path p : allScriptFiles) {
                    try {
                        String rawJson = Files.readString(p, StandardCharsets.UTF_8);
                        DslScene scene = SceneCodec.decode(rawJson);
                        if (scene == null || scene.id == null) continue;

                        boolean isSelected = selectedSceneIds.contains(scene.id) ||
                            selectedSceneIds.contains(scene.sceneKey());
                        if (!isSelected) {
                            String fileBase = p.getFileName().toString();
                            if (fileBase.endsWith(".json")) fileBase = fileBase.substring(0, fileBase.length() - 5);
                            String stripped = stripPackPrefix(fileBase);
                            isSelected = selectedSceneIds.stream()
                                .anyMatch(id -> id.equals(stripped) || id.endsWith(":" + stripped));
                        }

                        if (isSelected) {
                            scene.pack = normalizedName;
                            String cleanJson = SceneCodec.pretty(scene);
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

                count += writeStructuresToZip(zos, requiredStructures);
                LOGGER.info("Packed {} files into {} (selected {} scenes)", count, filename, selectedSceneIds.size());
            }

            return PackExportResult.success(outputPath, "Exported pack '" + normalizedName + "' to " + outputPath);
        } catch (IOException e) {
            PackExportResult failure = mapPackExportIoFailure(normalizedName, outputPath, e);
            LOGGER.error(failure.englishMessage(), e);
            return failure;
        }
    }

    public boolean packSelectedScenesAndStructures(String name, String version, String author, Set<String> selectedSceneIds) {
        return packSelectedScenesAndStructuresDetailed(name, version, author, selectedSceneIds).isSuccess();
    }

    PackExportResult validatePackExportName(String name) {
        if (name == null || name.isBlank()) {
            return PackExportResult.failure(
                "Cannot export pack because the pack name is blank",
                "ponderer.ui.export.name_empty"
            );
        }

        SafePaths.FileNameValidationError error = SafePaths.diagnosePortableAssetName(name);
        if (error == null) {
            return null;
        }

        return switch (error.code()) {
            case INVALID_CHARACTER -> PackExportResult.failure(
                "Cannot export pack '" + name + "' because the pack name contains invalid character '" + error.offendingText() + "'",
                SAVE_ERROR_KEY_PREFIX + "pack_name_invalid_char",
                error.offendingText()
            );
            case RESERVED_NAME -> PackExportResult.failure(
                "Cannot export pack '" + name + "' because the pack name is a reserved Windows name",
                SAVE_ERROR_KEY_PREFIX + "pack_name_reserved",
                name
            );
            case TRAILING_SPACE_OR_DOT -> PackExportResult.failure(
                "Cannot export pack '" + name + "' because the pack name ends with a space or dot",
                SAVE_ERROR_KEY_PREFIX + "pack_name_trailing"
            );
            case EMPTY, DOT_SEGMENT -> PackExportResult.failure(
                "Cannot export pack '" + name + "' because the pack name is invalid",
                SAVE_ERROR_KEY_PREFIX + "pack_name_invalid"
            );
        };
    }

    Path resolvePackExportOutputPath(Path resourcepacksDir, String packName) {
        String filename = "[Ponderer] " + packName + ".zip";
        return SafePaths.resolveFileName(resourcepacksDir, filename);
    }

    PackExportResult mapPackExportIoFailure(String packName, @javax.annotation.Nullable Path outputPath, IOException e) {
        String target = outputPath == null ? packName : outputPath.toString();
        if (e instanceof AccessDeniedException) {
            return PackExportResult.failure(
                "Failed to export pack '" + packName + "' to " + target + ": access denied",
                SAVE_ERROR_KEY_PREFIX + "access_denied",
                outputPath != null && outputPath.getFileName() != null ? outputPath.getFileName().toString() : target
            );
        }
        if (e instanceof NoSuchFileException) {
            return PackExportResult.failure(
                "Failed to export pack '" + packName + "' to " + target + ": target path does not exist",
                SAVE_ERROR_KEY_PREFIX + "path_missing",
                target
            );
        }

        String detail = "I/O error";
        if (e instanceof FileSystemException fileSystemException && fileSystemException.getReason() != null
            && !fileSystemException.getReason().isBlank()) {
            detail = "filesystem error: " + fileSystemException.getReason();
        } else if (e.getMessage() != null && !e.getMessage().isBlank()) {
            detail = "I/O error: " + e.getMessage();
        }

        return PackExportResult.failure(
            "Failed to export pack '" + packName + "' to " + target + ": " + detail,
            SAVE_ERROR_KEY_PREFIX + "io",
            detail
        );
    }

    List<Path> collectAllScriptFiles() {
        List<Path> result = new ArrayList<>();
        collectFilesWithExtension(workspace.layout.getSceneDir(), ".json", result);

        Path packsDir = workspace.layout.getPacksRoot();
        if (!Files.exists(packsDir)) {
            return result;
        }

        try (Stream<Path> packDirs = Files.list(packsDir)) {
            for (Path packDir : packDirs.filter(Files::isDirectory).toList()) {
                collectFilesWithExtension(workspace.layout.getPackContentSearchRoot(packDir, SCRIPT_DIR), ".json", result);
            }
        } catch (IOException ignored) {}

        return result;
    }

    void collectFilesWithExtension(@javax.annotation.Nullable Path root, String extension, List<Path> result) {
        if (root == null || !Files.exists(root)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                .filter(path -> !path.startsWith(root.resolve(PACKS_SUBDIR)))
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension))
                .forEach(result::add);
        } catch (IOException ignored) {
        }
    }

    String readAndUpdatePackField(Path path, String packName) {
        try {
            String rawJson = Files.readString(path, StandardCharsets.UTF_8);
            DslScene scene = SceneCodec.decode(rawJson);
            if (scene == null) return null;
            scene.pack = packName;
            return SceneCodec.pretty(scene);
        } catch (Exception e) {
            LOGGER.warn("Failed to read/update scene file: {}", path, e);
            return null;
        }
    }

    String stripPackPrefix(String filename) {
        String prefix = DslScene.extractPackPrefix(filename);
        if (prefix != null) {
            return filename.substring(prefix.length()).trim();
        }
        return filename;
    }

    String deduplicateFilename(String filename, Set<String> usedNames) {
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

    int writeStructuresToZip(ZipOutputStream zos, Set<String> structureRefs) throws IOException {
        int count = 0;
        List<Path> allStructureFiles = new ArrayList<>();
        collectFilesWithExtension(workspace.layout.getStructureDir(), ".nbt", allStructureFiles);

        Path packsDir = workspace.layout.getPacksRoot();
        if (Files.exists(packsDir)) {
            try (Stream<Path> packDirs = Files.list(packsDir)) {
                for (Path packDir : packDirs.filter(Files::isDirectory).toList()) {
                    collectFilesWithExtension(workspace.layout.getPackContentSearchRoot(packDir, STRUCTURE_DIR), ".nbt", allStructureFiles);
                }
            } catch (IOException ignored) {
            }
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

    public List<PonderPackInfo> scanAvailableSourcePacks() {
        Path resourcepacksDir = workspace.gameDir.resolve("resourcepacks");
        if (!Files.exists(resourcepacksDir)) {
            return List.of();
        }

        java.util.LinkedHashMap<String, PonderPackInfo> deduped = new java.util.LinkedHashMap<>();
        try (Stream<Path> paths = Files.list(resourcepacksDir)) {
            for (PonderPackInfo info : paths
                .filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
                .map(PonderPackInfo::fromZip)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing((PonderPackInfo info) -> info.name.toLowerCase(Locale.ROOT))
                    .thenComparing(info -> info.sourcePath.getFileName().toString().toLowerCase(Locale.ROOT)))
                .toList()) {
                PonderPackInfo existing = deduped.get(info.name);
                if (existing == null || info.lastModified >= existing.lastModified) {
                    deduped.put(info.name, info);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to scan resourcepacks directory", e);
        }

        return deduped.values().stream()
            .sorted(Comparator.comparing(info -> info.name.toLowerCase(Locale.ROOT)))
            .toList();
    }

    public PackImportResult importPackFromResourcePack(Path zipPath) {
        if (zipPath == null || !Files.exists(zipPath)) {
            PackImportResult failure = PackImportResult.failure(
                "Pack file not found: " + zipPath,
                "ponderer.ui.import.failed",
                "Pack file not found");
            LOGGER.warn(failure.englishMessage());
            return failure;
        }

        workspace.layout.ensureLocalPackLayoutMigrated();
        workspace.packState.load();
        PonderPackInfo info = PonderPackInfo.fromZip(zipPath);
        if (info == null) {
            PackImportResult failure = PackImportResult.failure(
                "Invalid Ponderer pack: " + zipPath,
                "ponderer.ui.import.failed",
                "Invalid Ponderer pack");
            LOGGER.warn(failure.englishMessage());
            return failure;
        }

        PackState.ImportedPackState existing = workspace.packState.getImportedPack(info.name);
        if (workspace.packState.isImported(info.name)) {
            if (existing != null && existing.importedVersion != null && existing.importedVersion.equals(info.version)) {
                return PackImportResult.failure(
                    "Pack '" + info.name + "' is already imported",
                    "ponderer.ui.import.already_imported",
                    info.name);
            }

            if (existing != null && existing.importedVersion != null && !existing.importedVersion.isBlank()) {
                return PackImportResult.failure(
                    "Pack '" + info.name + "' has a newer source version v" + info.version
                        + " while the imported local copy remains on v" + existing.importedVersion,
                    "ponderer.ui.import.newer_source",
                    info.name, info.version, existing.importedVersion);
            }

            return PackImportResult.failure(
                "Pack '" + info.name + "' already has an imported local copy",
                "ponderer.ui.import.already_imported",
                info.name);
        }

        Path packScriptsDir = workspace.layout.getPackSceneDir(info.name);
        Path packStructuresDir = workspace.layout.getPackStructureDir(info.name);
        if (packScriptsDir == null || packStructuresDir == null) {
            PackImportResult failure = PackImportResult.failure(
                "Cannot import pack '" + info.name + "' because the local destination path is unsafe",
                "ponderer.ui.import.failed",
                "Unsafe local destination");
            LOGGER.warn(failure.englishMessage());
            return failure;
        }

        try {
            int count = extractPackContents(zipPath, info, packScriptsDir, packStructuresDir);
            workspace.packState.markImported(info);
            return PackImportResult.success(
                count,
                "Imported pack '" + info.name + "' to local editable copy",
                "ponderer.ui.import.success",
                count, info.name);
        } catch (IOException e) {
            PackImportResult failure = PackImportResult.failure(
                "Failed to import pack '" + info.name + "': " + e.getMessage(),
                "ponderer.ui.import.failed",
                e.getMessage());
            LOGGER.warn(failure.englishMessage(), e);
            return failure;
        }
    }

    int extractPackContents(Path zipPath, PonderPackInfo info, Path packScriptsDir, Path packStructuresDir) throws IOException {
        int count = 0;
        Files.createDirectories(packScriptsDir);
        Files.createDirectories(packStructuresDir);

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                if (name.startsWith("data/ponderer/scripts/")) {
                    String fileName = name.substring("data/ponderer/scripts/".length());
                    if (fileName.isEmpty() || fileName.endsWith("/")) {
                        continue;
                    }
                    Path targetPath = workspace.layout.resolvePackScopedPath(packScriptsDir, info.name, fileName);
                    if (targetPath == null) {
                        LOGGER.warn("Skipping unsafe script zip entry '{}' in {}", fileName, zipPath);
                        continue;
                    }
                    Files.createDirectories(targetPath.getParent());
                    String json = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    Files.writeString(targetPath, injectPackField(json, info.name), StandardCharsets.UTF_8);
                    count++;
                } else if (name.startsWith("data/ponderer/structures/")) {
                    String fileName = name.substring("data/ponderer/structures/".length());
                    if (fileName.isEmpty() || fileName.endsWith("/")) {
                        continue;
                    }
                    Path targetPath = workspace.layout.resolvePackScopedPath(packStructuresDir, info.name, fileName);
                    if (targetPath == null) {
                        LOGGER.warn("Skipping unsafe structure zip entry '{}' in {}", fileName, zipPath);
                        continue;
                    }
                    Files.createDirectories(targetPath.getParent());
                    Files.write(targetPath, zis.readAllBytes());
                    count++;
                }
            }
        }

        return count;
    }

    void rebuildReadonlyCacheForPack(Path zipPath, PonderPackInfo info) throws IOException {
        Path packScriptsDir = workspace.layout.getReadonlyCachePackSceneDir(info.name);
        Path packStructuresDir = workspace.layout.getReadonlyCachePackStructureDir(info.name);
        if (packScriptsDir == null || packStructuresDir == null) {
            LOGGER.warn("Rejected unsafe readonly cache directories for pack {}", info.name);
            return;
        }

        extractPackContents(zipPath, info, packScriptsDir, packStructuresDir);
    }

    void clearReadonlyCache() {
        deleteDirectoryRecursive(workspace.layout.getReadonlyCacheRoot());
    }

    void deleteDirectoryRecursive(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    LOGGER.warn("Failed to delete {}", path, e);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to delete directory tree {}", dir, e);
        }
    }

    void reorganizeFilesForPack(String packName, List<Path> exportedFiles) {
        Path packScriptsDir = workspace.layout.getPackSceneDir(packName);
        if (packScriptsDir == null) {
            LOGGER.warn("Rejected unsafe pack script directory for pack {}", packName);
            return;
        }
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
                DslScene scene = SceneCodec.decode(rawJson);
                if (scene == null) continue;
                scene.pack = packName;
                String updatedJson = SceneCodec.pretty(scene);

                // Determine target filename: [PackName] clean_name.json
                String cleanFilename = stripPackPrefix(sourcePath.getFileName().toString());
                String prefixedFilename = "[" + packName + "] " + cleanFilename;
                Path targetPath = SafePaths.resolveFileName(packScriptsDir, prefixedFilename);
                if (targetPath == null) {
                    LOGGER.warn("Rejected unsafe pack script filename '{}' for pack {}", prefixedFilename, packName);
                    continue;
                }

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

    void reorganizeStructuresForPack(String packName) {
        Path packStructuresDir = workspace.layout.getPackStructureDir(packName);
        Path packScriptsDir = workspace.layout.getPackSceneDir(packName);
        if (packStructuresDir == null || packScriptsDir == null) {
            LOGGER.warn("Rejected unsafe pack directories for pack {}", packName);
            return;
        }

        // Collect structure references from pack scripts
        Set<String> structureRefs = new HashSet<>();
        if (Files.exists(packScriptsDir)) {
            try (Stream<Path> paths = Files.walk(packScriptsDir)) {
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
        Path structuresDir = workspace.layout.getStructureDir();
        if (Files.exists(structuresDir)) {
            try (Stream<Path> paths = Files.walk(structuresDir)) {
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
                        Path targetPath = SafePaths.resolveFileName(packStructuresDir, prefixedName);
                        if (targetPath == null) {
                            LOGGER.warn("Rejected unsafe pack structure filename '{}' for pack {}", prefixedName, packName);
                            continue;
                        }
                        if (!Files.exists(targetPath)) {
                            Files.copy(p, targetPath);
                        }
                        // Don't delete from flat dir — other scenes might still reference it
                    }
                }
            } catch (IOException ignored) {}
        }
    }

    void collectStructureReferences(String sceneJson, Set<String> structures) {
        try {
            collectStructureReferences(SceneCodec.decode(sceneJson), structures);
        } catch (Exception e) {
            LOGGER.warn("Failed to collect structure references from scene", e);
        }
    }

    public void collectStructureReferences(@javax.annotation.Nullable DslScene scene, Set<String> structures) {
        StructureReferences.collect(scene, structures);
    }

    String createPackMetadata(String name, String version, String author) {
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

    void updateRegistryAfterExport(Path zipPath, String name, String version, String author) {
        try {
            PonderPackInfo info = PonderPackInfo.fromZip(zipPath);
            if (info == null) return;
            String displayName = workspace.registry.getDisplayName(name);
            String fileHash = computeSha256(zipPath);
            workspace.registry.addOrUpdatePack(displayName, info, fileHash);
            LOGGER.info("Updated registry after export: {} v{}", displayName, version);
        } catch (Exception e) {
            LOGGER.warn("Failed to update registry after export", e);
        }
    }

    String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    void writeZipEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    public PackUpdateInfo loadPonderPackFromResourcePack(Path zipPath, boolean forceOverwrite) throws IOException {
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

        String displayName = workspace.registry.getDisplayName(info.name);
        String newFileHash = computeSha256(zipPath);
        String oldVersion = null;
        long loadedAtMillis = 0;

        PackRegistry.PackEntry existing = workspace.registry.getPack(displayName);
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
        workspace.registry.addOrUpdatePack(displayName, info, newFileHash);

        LOGGER.info("Loaded Ponderer pack: {} ({} files, {} conflicts)", info.name, count, conflicts);
        return new PackUpdateInfo(info.name, oldVersion, info.version, count, conflicts);
    }

    long parseLoadedAtMillis(@javax.annotation.Nullable String loadedAt) {
        if (loadedAt == null || loadedAt.isEmpty()) return 0;
        try {
            LocalDateTime ldt = LocalDateTime.parse(loadedAt, DateTimeFormatter.ISO_DATE_TIME);
            return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            LOGGER.warn("Failed to parse loadedAt timestamp: {}", loadedAt, e);
            return 0;
        }
    }

    int[] extractPonderPackWithBackup(Path zipPath, PonderPackInfo info, long loadedAtMillis) throws IOException {
        int count = 0;
        int conflicts = 0;
        Path packScriptsDir = workspace.layout.getPackSceneDir(info.name);
        Path packStructuresDir = workspace.layout.getPackStructureDir(info.name);
        if (packScriptsDir == null || packStructuresDir == null) {
            LOGGER.warn("Rejected unsafe extraction directories for pack {}", info.name);
            return new int[]{0, 0};
        }

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                if (name.startsWith("data/ponderer/scripts/")) {
                    String fileName = name.substring("data/ponderer/scripts/".length());
                    if (fileName.isEmpty() || fileName.endsWith("/")) continue;
                    Path targetPath = workspace.layout.resolvePackScopedPath(packScriptsDir, info.name, fileName);
                    if (targetPath == null) {
                        LOGGER.warn("Skipping unsafe script zip entry '{}' in {}", fileName, zipPath);
                        continue;
                    }

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
                    Path targetPath = workspace.layout.resolvePackScopedPath(packStructuresDir, info.name, fileName);
                    if (targetPath == null) {
                        LOGGER.warn("Skipping unsafe structure zip entry '{}' in {}", fileName, zipPath);
                        continue;
                    }

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

    String injectPackField(String json, String packName) {
        try {
            DslScene scene = SceneCodec.decode(json);
            if (scene != null) {
                scene.pack = packName;
                return SceneCodec.pretty(scene);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to inject pack field, writing raw content", e);
        }
        return json;
    }

    boolean backupIfModified(Path targetPath, long loadedAtMillis) {
        if (!Files.exists(targetPath)) return false;
        try {
            long fileModified = Files.getLastModifiedTime(targetPath).toMillis();
            // Only backup if the file was modified after the last load time
            // (meaning the user hand-edited it). Allow 5 second tolerance.
            if (loadedAtMillis > 0 && fileModified <= loadedAtMillis + 5000) {
                return false; // File was not modified by user, just overwrite
            }
            Path bakPath = SafePaths.resolveFileName(targetPath.getParent(), targetPath.getFileName().toString() + ".bak");
            if (bakPath == null) {
                LOGGER.warn("Rejected unsafe backup path for {}", targetPath);
                return false;
            }
            Files.copy(targetPath, bakPath, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Backed up user-modified file: {} -> {}", targetPath.getFileName(), bakPath.getFileName());
            return true;
        } catch (IOException e) {
            LOGGER.warn("Failed to backup file: {}", targetPath, e);
            return false;
        }
    }

    boolean packScriptFilesExist(String packName) {
        Path packDir = workspace.layout.getPackSceneDir(packName);
        if (packDir == null) {
            return false;
        }
        if (Files.exists(packDir)) {
            try (Stream<Path> paths = Files.walk(packDir)) {
                if (paths.anyMatch(path -> Files.isRegularFile(path)
                        && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))) {
                    return true;
                }
            } catch (IOException ignored) {}
        }
        return false;
    }

    String computeSha256(Path file) {
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

    public AutoLoadResult autoLoadPonderPacks() {
        workspace.layout.ensureLocalPackLayoutMigrated();
        workspace.packState.load();
        clearReadonlyCache();
        List<PackUpdateInfo> updates = new ArrayList<>();
        Set<String> presentSourcePacks = new HashSet<>();

        for (PonderPackInfo info : scanAvailableSourcePacks()) {
            presentSourcePacks.add(info.name);
            workspace.packState.updateCurrentSource(info);

            if (workspace.packState.isImported(info.name)) {
                PackState.ImportedPackState imported = workspace.packState.getImportedPack(info.name);
                if (imported != null && workspace.packState.shouldNotifySourceUpdate(info.name, info.version)) {
                    updates.add(new PackUpdateInfo(info.name, imported.importedVersion, info.version, 0, 0));
                    workspace.packState.markNotified(info.name, info.version);
                }
                continue;
            }

            try {
                rebuildReadonlyCacheForPack(info.sourcePath, info);
            } catch (IOException e) {
                LOGGER.warn("Failed to build readonly cache for pack {}", info.sourcePath, e);
            }
        }

        workspace.packState.clearMissingSources(presentSourcePacks);
        return new AutoLoadResult(List.of(), updates);
    }

    public void removeOrphanedPacks(List<String> displayNames) {
        for (String name : displayNames) {
            workspace.packState.removeImportedPack(name);
            LOGGER.info("Removed imported pack state: {}", name);
        }
    }
}
