package com.nododiiiii.ponderer.workspace.local;

import com.nododiiiii.ponderer.ponder.SyncMeta;
import com.nododiiiii.ponderer.scene.SceneCodec;
import com.nododiiiii.ponderer.scene.SceneNormalizer;
import com.nododiiiii.ponderer.scene.DslScene;
import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.util.SafePaths;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import com.nododiiiii.ponderer.workspace.api.*;
import com.nododiiiii.ponderer.workspace.api.WorkspaceResults.*;

/** Internal file implementation. Construct through WorkspaceFiles. */
public final class LocalSceneRepository implements SceneRepository {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BASE_DIR = "ponderer";
    private static final String SCRIPT_DIR = "scripts";
    private static final String STRUCTURE_DIR = "structures";
    private static final String PACKS_SUBDIR = "_packs";
    private static final String SAVE_ERROR_KEY_PREFIX = "ponderer.ui.save_error.";
    private final WorkspaceFiles workspace;
    LocalSceneRepository(WorkspaceFiles workspace) { this.workspace = workspace; }

    public DslScene copyScene(@javax.annotation.Nullable DslScene scene) {
        return SceneCodec.copy(scene);
    }

    public String displaySceneKey(String id, @javax.annotation.Nullable String pack) {
        if (pack == null || pack.isBlank()) {
            return id;
        }
        return "[" + pack + "] " + id;
    }

    public boolean saveToServer(MinecraftServer server, String sceneId, @javax.annotation.Nullable String pack, String json) {
        ResourceLocation sceneLoc = ResourceLocation.tryParse(sceneId);
        if (sceneLoc == null) {
            LOGGER.warn("Invalid scene id: {}", sceneId);
            return false;
        }

        Path scenePath = workspace.layout.resolveServerScenePath(server, sceneLoc, pack);
        if (scenePath == null) {
            LOGGER.warn("Rejected unsafe scene path for id {} pack {}", sceneId, pack);
            return false;
        }

        try {
            Files.createDirectories(scenePath.getParent());
            Files.writeString(scenePath, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to write scene json: {}", scenePath, e);
            return false;
        }

        LOGGER.info("Uploaded scene {} to server storage (pack={})", sceneId, pack);
        return true;
    }

    public List<SyncFileRef> collectServerScriptRefs(MinecraftServer server) {
        workspace.layout.ensureServerPackLayoutMigrated(server);

        Path flatRoot = workspace.layout.getServerSceneDir(server);
        Path packsRoot = workspace.layout.getServerPacksRoot(server);
        if (!Files.exists(flatRoot) && !Files.exists(packsRoot)) {
            return List.of();
        }

        List<SyncFileRef> entries = new ArrayList<>();
        collectServerSceneRefsFromRoot(flatRoot, null, entries);

        if (!Files.exists(packsRoot)) {
            return entries;
        }

        try (Stream<Path> packDirs = Files.list(packsRoot)) {
            for (Path packDir : packDirs.filter(Files::isDirectory).sorted().toList()) {
                String packId = packDir.getFileName() != null ? packDir.getFileName().toString() : null;
                if (packId == null || packId.isBlank()) {
                    continue;
                }
                collectServerSceneRefsFromRoot(packDir.resolve(SCRIPT_DIR), packId, entries);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to collect packed server scripts", e);
        }
        return entries;
    }

    void collectServerSceneRefsFromRoot(Path root, @javax.annotation.Nullable String explicitPack, List<SyncFileRef> entries) {
        if (root == null || !Files.exists(root)) {
            return;
        }

        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .filter(path -> !path.startsWith(root.resolve(PACKS_SUBDIR)))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList()) {
                SyncFileRef ref = toServerSceneRef(root, path, explicitPack);
                if (ref != null) {
                    entries.add(ref);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to collect server scripts from {}", root, e);
        }
    }

    SyncFileRef toServerSceneRef(Path root, Path file, @javax.annotation.Nullable String explicitPack) {
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            DslScene scene = SceneCodec.decode(json);
            if (scene == null || scene.id == null || scene.id.isBlank()) {
                LOGGER.warn("Skipping invalid server scene file (missing id): {}", file);
                return null;
            }
            String inferredPack = explicitPack != null && !explicitPack.isBlank() ? explicitPack : inferPackName(root, file);
            String pack = scene.pack != null && !scene.pack.isBlank() ? scene.pack : inferredPack;
            return new SyncFileRef(scene.id, pack, file);
        } catch (Exception e) {
            LOGGER.warn("Failed to inspect server scene file: {}", file, e);
            return null;
        }
    }

    String inferPackName(Path root, Path file) {
        Path rel = root.relativize(file);
        if (rel.getNameCount() >= 2 && PACKS_SUBDIR.equals(rel.getName(0).toString())) {
            return rel.getName(1).toString();
        }
        return null;
    }

    public Path findLocalSceneFile(String sceneId) {
        return findLocalSceneFile(sceneId, null);
    }

    public Path findLocalSceneFile(String sceneId) {
        return findLocalSceneFile(sceneId, null);
    }

    public synchronized LocalSaveResult saveSceneToLocalDetailed(DslScene scene) {
        LocalSaveResult validationFailure = validateLocalSceneSave(scene);
        if (validationFailure != null) {
            LOGGER.warn(validationFailure.englishMessage());
            return validationFailure;
        }

        Path filePath = workspace.layout.resolveLocalScenePath(scene);
        if (filePath == null) {
            LocalSaveResult failure = LocalSaveResult.failure(
                "Cannot save scene '" + scene.id + "' because the output path could not be resolved safely",
                SAVE_ERROR_KEY_PREFIX + "file_name_invalid"
            );
            LOGGER.warn(failure.englishMessage());
            return failure;
        }

        try {
            Files.createDirectories(filePath.getParent());
            String json = serializeLocalScene(scene);
            Path temporary = Files.createTempFile(filePath.getParent(), ".scene-", ".tmp");
            try {
                Files.writeString(temporary, json, StandardCharsets.UTF_8);
                try { Files.move(temporary, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temporary, filePath, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temporary); }
            if (scene.pack != null && !scene.pack.isBlank()) {
                workspace.packs.deleteDirectoryRecursive(workspace.layout.getReadonlyCachePackSceneDir(scene.pack));
                workspace.packs.deleteDirectoryRecursive(workspace.layout.getReadonlyCachePackStructureDir(scene.pack));
            }
            LOGGER.info("Saved scene {} to {}", scene.id, filePath);
            return LocalSaveResult.success(filePath, "Saved scene '" + scene.id + "' to " + filePath);
        } catch (IOException e) {
            LocalSaveResult failure = mapIoFailure(scene.id, filePath, e);
            LOGGER.error(failure.englishMessage(), e);
            return failure;
        }
    }

    public boolean saveSceneToLocal(DslScene scene) {
        return saveSceneToLocalDetailed(scene).isSuccess();
    }

    public String serializeLocalScene(DslScene scene) {
        sanitizeScene(scene);
        return SceneCodec.pretty(scene);
    }

    Path findExistingFileForScene(DslScene scene) {
        if (scene.pack != null && !scene.pack.isEmpty()) {
            Path packDir = workspace.layout.getPackSceneDir(scene.pack);
            return packDir == null ? null : findExistingFile(packDir, scene.id);
        }
        return findExistingFile(workspace.layout.getSceneDir(), scene.id);
    }

    Path findExistingFile(Path dir, String sceneId) {
        if (!Files.exists(dir)) return null;
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")).toList()) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    DslScene existing = SceneCodec.decode(reader);
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

    public boolean deleteSceneLocal(String sceneId) {
        if (sceneId == null || sceneId.isBlank()) return false;
        // Search flat directory first, then all pack subdirectories
        Path existing = findExistingFile(workspace.layout.getSceneDir(), sceneId);
        if (existing == null) {
            existing = findExistingFileInPacks(sceneId);
        }
        if (existing == null) {
            LOGGER.warn("No local file found for scene id: {}", sceneId);
            return false;
        }
        try {
            Files.deleteIfExists(existing);
            SyncMeta.recordLocalSceneDeletion(sceneId, existing);
            LOGGER.info("Deleted scene file: {}", existing);
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to delete scene file: {}", existing, e);
            return false;
        }
    }

    Path findExistingFileInPacks(String sceneId) {
        Path packsDir = workspace.layout.getPacksRoot();
        if (!Files.exists(packsDir)) return null;
        try (Stream<Path> packDirs = Files.list(packsDir)) {
            for (Path packDir : packDirs.filter(Files::isDirectory).toList()) {
                Path searchRoot = workspace.layout.getPackContentSearchRoot(packDir, SCRIPT_DIR);
                if (searchRoot == null || !Files.exists(searchRoot)) {
                    continue;
                }
                Path found = findExistingFile(searchRoot, sceneId);
                if (found != null) return found;
            }
        } catch (IOException ignored) {}
        return null;
    }

    Path findExistingFileByKey(String sceneKey) {
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
        Path searchDir = packName != null ? workspace.layout.getPackSceneDir(packName) : workspace.layout.getSceneDir();
        if (searchDir == null) {
            return null;
        }
        Path result = findExistingFile(searchDir, sceneId);
        if (result != null) return result;

        // Fallback: search flat directory even for pack scenes
        if (packName != null) {
            return findExistingFile(workspace.layout.getSceneDir(), sceneId);
        }
        return null;
    }

    public boolean deleteSceneByKey(String sceneKey) {
        if (sceneKey == null || sceneKey.isBlank()) return false;
        Path existing = findExistingFileByKey(sceneKey);
        if (existing == null) {
            LOGGER.warn("No local file found for scene key: {}", sceneKey);
            return false;
        }
        try {
            Files.deleteIfExists(existing);
            String packPrefix = DslScene.extractPackPrefix(sceneKey);
            String sceneId = packPrefix == null ? sceneKey : sceneKey.substring(packPrefix.length()).trim();
            SyncMeta.recordLocalSceneDeletion(sceneId, existing);
            LOGGER.info("Deleted scene file: {}", existing);
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to delete scene file: {}", existing, e);
            return false;
        }
    }

    public List<DslScene> loadScenes() {
        workspace.layout.ensureLocalPackLayoutMigrated();

        Path dir = workspace.layout.getSceneDir();
        List<DslScene> loaded = new ArrayList<>();

        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("Failed to create ponderer scene directory: {}", dir, e);
                        return List.of();
        }

        // 1. Load flat files (local scenes)
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                .filter(path -> !path.startsWith(dir.resolve(PACKS_SUBDIR)))
                .sorted(Comparator.comparing(Path::toString))
                .forEach(path -> loadSceneFile(path, loaded));
        } catch (IOException e) {
            LOGGER.error("Failed to list scene directory: {}", dir, e);
        }

        // 2. Load imported local packs (_packs/{PackName}/scripts)
        loadPackScenesFromRoot(workspace.layout.getPacksRoot(), loaded);

        // 3. Load readonly cached source packs after imported locals.
        loadPackScenesFromRoot(workspace.layout.getReadonlyCachePacksRoot(), loaded);

        LOGGER.info("Loaded {} ponderer scene(s) from {}", loaded.size(), dir);
        return List.copyOf(loaded);
    }

    void loadPackScenesFromRoot(Path packsDir, List<DslScene> loaded) {
        if (!Files.exists(packsDir)) {
            return;
        }

        try (Stream<Path> packDirs = Files.list(packsDir)) {
            for (Path packDir : packDirs.filter(Files::isDirectory).sorted().toList()) {
                Path searchRoot = workspace.layout.getPackContentSearchRoot(packDir, SCRIPT_DIR);
                if (searchRoot == null || !Files.exists(searchRoot)) {
                    continue;
                }

                try (Stream<Path> paths = Files.walk(searchRoot)) {
                    paths.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
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

    void loadSceneFile(Path path, List<DslScene> loaded) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            DslScene scene = SceneCodec.decode(reader);
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

    public void sanitizeScene(DslScene scene) {
        SceneNormalizer.normalize(scene, workspace.defaultEditable.getAsBoolean());
    }

    LocalSaveResult validateLocalSceneSave(DslScene scene) {
        if (scene == null || scene.id == null || scene.id.isBlank()) {
            return LocalSaveResult.failure(
                "Cannot save scene with blank id",
                SAVE_ERROR_KEY_PREFIX + "scene_id_blank"
            );
        }

        String invalidSceneIdChar = findInvalidSceneIdCharacter(scene.id);
        ResourceLocation loc = ResourceLocation.tryParse(scene.id);
        if (loc == null) {
            if (invalidSceneIdChar != null) {
                return LocalSaveResult.failure(
                    "Cannot save scene '" + scene.id + "' because the scene id contains invalid character '" + invalidSceneIdChar + "'",
                    SAVE_ERROR_KEY_PREFIX + "scene_id_invalid_char",
                    invalidSceneIdChar
                );
            }
            return LocalSaveResult.failure(
                "Cannot save scene '" + scene.id + "' because the scene id is invalid",
                SAVE_ERROR_KEY_PREFIX + "scene_id_invalid"
            );
        }

        if (scene.pack != null && !scene.pack.isBlank()) {
            SafePaths.FileNameValidationError packError = SafePaths.diagnosePortableAssetName(scene.pack);
            if (packError != null) {
                return toPackNameFailure(scene.id, scene.pack, packError);
            }
        }

        String fileName = buildLocalSceneFileName(scene.pack, loc);
        SafePaths.FileNameValidationError fileNameError = SafePaths.diagnoseWindowsFileNameSegment(fileName);
        if (fileNameError != null) {
            return toFileNameFailure(scene.id, fileName, fileNameError);
        }

        return null;
    }

    String buildLocalSceneFileName(@javax.annotation.Nullable String packName, ResourceLocation loc) {
        String prefix = packName != null && !packName.isBlank() ? "[" + packName + "] " : "";
        return prefix + loc.getPath().replace('/', '_') + ".json";
    }

    String findInvalidSceneIdCharacter(String sceneId) {
        if (sceneId == null) {
            return null;
        }
        for (int i = 0; i < sceneId.length(); i++) {
            char ch = sceneId.charAt(i);
            boolean allowed = (ch >= 'a' && ch <= 'z')
                || (ch >= '0' && ch <= '9')
                || ch == '_'
                || ch == '-'
                || ch == '.'
                || ch == '/'
                || ch == ':';
            if (!allowed) {
                if (Character.isISOControl(ch)) {
                    return String.format(Locale.ROOT, "U+%04X", (int) ch);
                }
                return Character.toString(ch);
            }
        }
        return null;
    }

    LocalSaveResult toPackNameFailure(String sceneId, String packName, SafePaths.FileNameValidationError error) {
        return switch (error.code()) {
            case INVALID_CHARACTER -> LocalSaveResult.failure(
                "Cannot save scene '" + sceneId + "' because pack name '" + packName + "' contains invalid character '" + error.offendingText() + "'",
                SAVE_ERROR_KEY_PREFIX + "pack_name_invalid_char",
                error.offendingText()
            );
            case RESERVED_NAME -> LocalSaveResult.failure(
                "Cannot save scene '" + sceneId + "' because pack name '" + packName + "' is a reserved Windows name",
                SAVE_ERROR_KEY_PREFIX + "pack_name_reserved",
                packName
            );
            case TRAILING_SPACE_OR_DOT -> LocalSaveResult.failure(
                "Cannot save scene '" + sceneId + "' because pack name '" + packName + "' ends with a space or dot",
                SAVE_ERROR_KEY_PREFIX + "pack_name_trailing"
            );
            case EMPTY, DOT_SEGMENT -> LocalSaveResult.failure(
                "Cannot save scene '" + sceneId + "' because pack name '" + packName + "' is invalid",
                SAVE_ERROR_KEY_PREFIX + "pack_name_invalid"
            );
        };
    }

    LocalSaveResult toFileNameFailure(String sceneId, String fileName, SafePaths.FileNameValidationError error) {
        return switch (error.code()) {
            case INVALID_CHARACTER -> LocalSaveResult.failure(
                "Cannot save scene '" + sceneId + "' because output filename '" + fileName + "' contains invalid character '" + error.offendingText() + "'",
                SAVE_ERROR_KEY_PREFIX + "file_name_invalid_char",
                error.offendingText()
            );
            case RESERVED_NAME -> LocalSaveResult.failure(
                "Cannot save scene '" + sceneId + "' because output filename '" + fileName + "' is a reserved Windows name",
                SAVE_ERROR_KEY_PREFIX + "file_name_reserved",
                fileName
            );
            case TRAILING_SPACE_OR_DOT -> LocalSaveResult.failure(
                "Cannot save scene '" + sceneId + "' because output filename '" + fileName + "' ends with a space or dot",
                SAVE_ERROR_KEY_PREFIX + "file_name_trailing"
            );
            case EMPTY, DOT_SEGMENT -> LocalSaveResult.failure(
                "Cannot save scene '" + sceneId + "' because output filename '" + fileName + "' is invalid",
                SAVE_ERROR_KEY_PREFIX + "file_name_invalid"
            );
        };
    }

    LocalSaveResult mapIoFailure(String sceneId, Path filePath, IOException e) {
        if (e instanceof AccessDeniedException) {
            return LocalSaveResult.failure(
                "Failed to save scene '" + sceneId + "' to " + filePath + ": access denied",
                SAVE_ERROR_KEY_PREFIX + "access_denied",
                filePath.getFileName() != null ? filePath.getFileName().toString() : filePath.toString()
            );
        }
        if (e instanceof NoSuchFileException) {
            return LocalSaveResult.failure(
                "Failed to save scene '" + sceneId + "' to " + filePath + ": target path does not exist",
                SAVE_ERROR_KEY_PREFIX + "path_missing",
                filePath.toString()
            );
        }

        String detail = "I/O error";
        if (e instanceof FileSystemException fileSystemException && fileSystemException.getReason() != null
            && !fileSystemException.getReason().isBlank()) {
            detail = "filesystem error: " + fileSystemException.getReason();
        } else if (e.getMessage() != null && !e.getMessage().isBlank()) {
            detail = "I/O error: " + e.getMessage();
        }

        return LocalSaveResult.failure(
            "Failed to save scene '" + sceneId + "' to " + filePath + ": " + detail,
            SAVE_ERROR_KEY_PREFIX + "io",
            detail
        );
    }
}
