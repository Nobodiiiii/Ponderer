package com.nododiiiii.ponderer.ponder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.util.SafePaths;
import net.minecraft.resources.ResourceLocation;
import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
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
import java.util.Arrays;
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

    public record SyncFileRef(String id, @javax.annotation.Nullable String pack, Path path) {
    }

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

    public static final class LocalSaveResult {
        private final boolean success;
        @javax.annotation.Nullable
        private final Path path;
        private final String englishMessage;
        @javax.annotation.Nullable
        private final String uiMessageKey;
        private final Object[] uiMessageArgs;

        private LocalSaveResult(boolean success, @javax.annotation.Nullable Path path, String englishMessage,
                                @javax.annotation.Nullable String uiMessageKey, Object[] uiMessageArgs) {
            this.success = success;
            this.path = path;
            this.englishMessage = englishMessage;
            this.uiMessageKey = uiMessageKey;
            this.uiMessageArgs = uiMessageArgs == null ? new Object[0] : Arrays.copyOf(uiMessageArgs, uiMessageArgs.length);
        }

        public boolean isSuccess() {
            return success;
        }

        @javax.annotation.Nullable
        public Path path() {
            return path;
        }

        public String englishMessage() {
            return englishMessage;
        }

        @javax.annotation.Nullable
        public String uiMessageKey() {
            return uiMessageKey;
        }

        public Object[] uiMessageArgs() {
            return Arrays.copyOf(uiMessageArgs, uiMessageArgs.length);
        }

        private static LocalSaveResult success(Path path, String englishMessage) {
            return new LocalSaveResult(true, path, englishMessage, null, new Object[0]);
        }

        private static LocalSaveResult failure(String englishMessage, String uiMessageKey, Object... uiMessageArgs) {
            return new LocalSaveResult(false, null, englishMessage, uiMessageKey, uiMessageArgs);
        }
    }

    public static final class PackExportResult {
        private final boolean success;
        @javax.annotation.Nullable
        private final Path path;
        private final String englishMessage;
        @javax.annotation.Nullable
        private final String uiMessageKey;
        private final Object[] uiMessageArgs;

        private PackExportResult(boolean success, @javax.annotation.Nullable Path path, String englishMessage,
                                 @javax.annotation.Nullable String uiMessageKey, Object[] uiMessageArgs) {
            this.success = success;
            this.path = path;
            this.englishMessage = englishMessage;
            this.uiMessageKey = uiMessageKey;
            this.uiMessageArgs = uiMessageArgs == null ? new Object[0] : Arrays.copyOf(uiMessageArgs, uiMessageArgs.length);
        }

        public boolean isSuccess() {
            return success;
        }

        @javax.annotation.Nullable
        public Path path() {
            return path;
        }

        public String englishMessage() {
            return englishMessage;
        }

        @javax.annotation.Nullable
        public String uiMessageKey() {
            return uiMessageKey;
        }

        public Object[] uiMessageArgs() {
            return Arrays.copyOf(uiMessageArgs, uiMessageArgs.length);
        }

        private static PackExportResult success(Path path, String englishMessage) {
            return new PackExportResult(true, path, englishMessage, null, new Object[0]);
        }

        private static PackExportResult failure(String englishMessage, String uiMessageKey, Object... uiMessageArgs) {
            return new PackExportResult(false, null, englishMessage, uiMessageKey, uiMessageArgs);
        }
    }

    public static final class PackImportResult {
        private final boolean success;
        private final int fileCount;
        private final String englishMessage;
        @javax.annotation.Nullable
        private final String uiMessageKey;
        private final Object[] uiMessageArgs;

        private PackImportResult(boolean success, int fileCount, String englishMessage,
                                 @javax.annotation.Nullable String uiMessageKey, Object[] uiMessageArgs) {
            this.success = success;
            this.fileCount = fileCount;
            this.englishMessage = englishMessage;
            this.uiMessageKey = uiMessageKey;
            this.uiMessageArgs = uiMessageArgs == null ? new Object[0] : Arrays.copyOf(uiMessageArgs, uiMessageArgs.length);
        }

        public boolean isSuccess() {
            return success;
        }

        public int fileCount() {
            return fileCount;
        }

        public String englishMessage() {
            return englishMessage;
        }

        @javax.annotation.Nullable
        public String uiMessageKey() {
            return uiMessageKey;
        }

        public Object[] uiMessageArgs() {
            return Arrays.copyOf(uiMessageArgs, uiMessageArgs.length);
        }

        private static PackImportResult success(int fileCount, String englishMessage, String uiMessageKey, Object... uiMessageArgs) {
            return new PackImportResult(true, fileCount, englishMessage, uiMessageKey, uiMessageArgs);
        }

        private static PackImportResult failure(String englishMessage, String uiMessageKey, Object... uiMessageArgs) {
            return new PackImportResult(false, 0, englishMessage, uiMessageKey, uiMessageArgs);
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
    private static final String SAVE_ERROR_KEY_PREFIX = "ponderer.ui.save_error.";

    private SceneStore() {
    }

    @javax.annotation.Nullable
    public static DslScene copyScene(@javax.annotation.Nullable DslScene scene) {
        if (scene == null) {
            return null;
        }
        return GSON.fromJson(GSON.toJson(scene), DslScene.class);
    }

    public static Path getSceneDir() {
        return PondererServices.PLATFORM.getConfigDir().resolve(BASE_DIR).resolve(SCRIPT_DIR);
    }

    public static Path getStructureDir() {
        return PondererServices.PLATFORM.getConfigDir().resolve(BASE_DIR).resolve(STRUCTURE_DIR);
    }

    public static Path getReadonlyCacheRoot() {
        return PondererServices.PLATFORM.getConfigDir().resolve(BASE_DIR).resolve(".cache").resolve("readonly");
    }

    public static Path getPacksRoot() {
        return PondererServices.PLATFORM.getConfigDir().resolve(BASE_DIR).resolve(PACKS_SUBDIR);
    }

    private static Path getReadonlyCachePacksRoot() {
        return getReadonlyCacheRoot().resolve(PACKS_SUBDIR);
    }

    private static Path getLegacyPackSceneRoot() {
        return getSceneDir().resolve(PACKS_SUBDIR);
    }

    private static Path getLegacyPackStructureRoot() {
        return getStructureDir().resolve(PACKS_SUBDIR);
    }

    private static Path getServerPacksRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(BASE_DIR).resolve(PACKS_SUBDIR);
    }

    private static Path getLegacyServerPackSceneRoot(MinecraftServer server) {
        return getServerSceneDir(server).resolve(PACKS_SUBDIR);
    }

    private static Path getLegacyServerPackStructureRoot(MinecraftServer server) {
        return getServerStructureDir(server).resolve(PACKS_SUBDIR);
    }

    @javax.annotation.Nullable
    private static Path resolvePackTypeDir(@javax.annotation.Nullable Path packDir, String typeDir) {
        return packDir == null ? null : SafePaths.resolveFileName(packDir, typeDir);
    }

    @javax.annotation.Nullable
    public static Path getReadonlyCachePackDir(String packName) {
        return SafePaths.resolveFileName(getReadonlyCachePacksRoot(), packName);
    }

    @javax.annotation.Nullable
    public static Path getReadonlyCachePackSceneDir(String packName) {
        return resolvePackTypeDir(getReadonlyCachePackDir(packName), SCRIPT_DIR);
    }

    @javax.annotation.Nullable
    public static Path getReadonlyCachePackStructureDir(String packName) {
        return resolvePackTypeDir(getReadonlyCachePackDir(packName), STRUCTURE_DIR);
    }

    @javax.annotation.Nullable
    public static Path getPackDir(String packName) {
        return SafePaths.resolveFileName(getPacksRoot(), packName);
    }

    @javax.annotation.Nullable
    public static Path getPackSceneDir(String packName) {
        return resolvePackTypeDir(getPackDir(packName), SCRIPT_DIR);
    }

    @javax.annotation.Nullable
    public static Path getPackStructureDir(String packName) {
        return resolvePackTypeDir(getPackDir(packName), STRUCTURE_DIR);
    }

    @javax.annotation.Nullable
    private static Path getLegacyPackSceneDir(String packName) {
        return SafePaths.resolveFileName(getLegacyPackSceneRoot(), packName);
    }

    @javax.annotation.Nullable
    private static Path getLegacyPackStructureDir(String packName) {
        return SafePaths.resolveFileName(getLegacyPackStructureRoot(), packName);
    }

    @javax.annotation.Nullable
    private static Path getServerPackDir(MinecraftServer server, String packName) {
        return SafePaths.resolveFileName(getServerPacksRoot(server), packName);
    }

    @javax.annotation.Nullable
    public static Path getServerPackSceneDir(MinecraftServer server, String packName) {
        return resolvePackTypeDir(getServerPackDir(server, packName), SCRIPT_DIR);
    }

    @javax.annotation.Nullable
    public static Path getServerPackStructureDir(MinecraftServer server, String packName) {
        return resolvePackTypeDir(getServerPackDir(server, packName), STRUCTURE_DIR);
    }

    @javax.annotation.Nullable
    public static Path getStructurePath(String path) {
        return SafePaths.resolveRelativePath(getStructureDir(), path + ".nbt");
    }

    @javax.annotation.Nullable
    public static Path getStructurePath(ResourceLocation id) {
        return resolveStructurePath(getStructureDir(), id);
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
            Path packPath = resolvePackScopedPath(getPackStructureDir(packName), packName, path + ".nbt");
            if (packPath != null && Files.exists(packPath)) return packPath;
            Path readonlyPackPath = resolvePackScopedPath(getReadonlyCachePackStructureDir(packName), packName, path + ".nbt");
            if (readonlyPackPath != null && Files.exists(readonlyPackPath)) return readonlyPackPath;
        }
        // 2. Fall back to flat directory
        Path flatPath = SafePaths.resolveRelativePath(getStructureDir(), path + ".nbt");
        if (flatPath != null && Files.exists(flatPath)) return flatPath;
        // 3. Search local imported pack subdirectories
        Path importedPath = findStructureInPackRoots(getPacksRoot(), path);
        if (importedPath != null) return importedPath;
        // 4. Search readonly cached source packs
        return findStructureInPackRoots(getReadonlyCachePacksRoot(), path);
    }

    private static Path getPackContentSearchRoot(Path packDir, String typeDir) {
        if (packDir == null) {
            return null;
        }

        Path typedRoot = packDir.resolve(typeDir);
        return Files.isDirectory(typedRoot) ? typedRoot : packDir;
    }

    @javax.annotation.Nullable
    private static Path findStructureInPackRoots(Path packsDir, String path) {
        if (!Files.exists(packsDir)) {
            return null;
        }

        try (Stream<Path> packDirs = Files.list(packsDir)) {
            for (Path packDir : packDirs.filter(Files::isDirectory).toList()) {
                Path searchRoot = getPackContentSearchRoot(packDir, STRUCTURE_DIR);
                if (searchRoot == null || !Files.exists(searchRoot)) {
                    continue;
                }

                try (Stream<Path> files = Files.walk(searchRoot)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) {
                        String fname = file.getFileName().toString();
                        if (!fname.endsWith(".nbt")) {
                            continue;
                        }

                        Path relative = searchRoot.relativize(file);
                        if (relative.getNameCount() == 0) {
                            continue;
                        }

                        String first = relative.getName(0).toString();
                        String prefix = DslScene.extractPackPrefix(first);
                        if (prefix != null) {
                            first = first.substring(prefix.length()).trim();
                        }

                        String candidate = relative.getNameCount() == 1
                            ? first
                            : first + "/" + relative.subpath(1, relative.getNameCount()).toString().replace("\\", "/");
                        if (candidate.endsWith(".nbt")) {
                            candidate = candidate.substring(0, candidate.length() - 4);
                        }

                        String defaultNamespaceCandidate = candidate.startsWith(Ponderer.MODID + "/")
                            ? candidate.substring((Ponderer.MODID + "/").length())
                            : candidate;

                        if (candidate.equals(path) || defaultNamespaceCandidate.equals(path)) {
                            return file;
                        }
                    }
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    public static Path getServerSceneDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(BASE_DIR).resolve(SCRIPT_DIR);
    }

    public static Path getServerStructureDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(BASE_DIR).resolve(STRUCTURE_DIR);
    }

    @javax.annotation.Nullable
    private static Path resolveScenePath(Path root, ResourceLocation id) {
        return SafePaths.resolveNamespacedPath(root, id, Ponderer.MODID, ".json");
    }

    @javax.annotation.Nullable
    private static Path resolveStructurePath(Path root, ResourceLocation id) {
        return SafePaths.resolveNamespacedPath(root, id, Ponderer.MODID, ".nbt");
    }

    @javax.annotation.Nullable
    private static Path resolveServerNamespacedPath(Path root, ResourceLocation id, String extension) {
        return SafePaths.resolveRelativePath(root, id.getNamespace() + "/" + id.getPath() + extension);
    }

    @javax.annotation.Nullable
    private static Path resolvePackScopedPath(Path packDir, String packName, String relativePath) {
        List<String> segments = SafePaths.splitValidatedRelativePath(relativePath);
        if (packDir == null || segments == null || segments.isEmpty()) {
            return null;
        }
        List<String> targetSegments = new ArrayList<>(segments);
        targetSegments.set(0, "[" + packName + "] " + targetSegments.get(0));
        return SafePaths.resolveRelativePath(packDir, targetSegments);
    }

    @javax.annotation.Nullable
    public static Path resolveServerScenePath(MinecraftServer server, ResourceLocation id, @javax.annotation.Nullable String pack) {
        if (pack != null && !pack.isBlank()) {
            ensureServerPackLayoutMigrated(server);
            Path packDir = getServerPackSceneDir(server, pack);
            return packDir == null ? null : resolvePackScopedPath(packDir, pack, id.getNamespace() + "/" + id.getPath() + ".json");
        }
        return resolveServerNamespacedPath(getServerSceneDir(server), id, ".json");
    }

    @javax.annotation.Nullable
    public static Path resolveServerStructurePath(MinecraftServer server, ResourceLocation id,
            @javax.annotation.Nullable String pack) {
        if (pack != null && !pack.isBlank()) {
            ensureServerPackLayoutMigrated(server);
            Path packDir = getServerPackStructureDir(server, pack);
            return packDir == null ? null : resolvePackScopedPath(packDir, pack, id.getNamespace() + "/" + id.getPath() + ".nbt");
        }
        return resolveServerNamespacedPath(getServerStructureDir(server), id, ".nbt");
    }

    @javax.annotation.Nullable
    public static Path resolveLocalSyncStructurePath(ResourceLocation id, @javax.annotation.Nullable String pack) {
        if (pack != null && !pack.isBlank()) {
            Path packDir = getPackStructureDir(pack);
            if (packDir == null) {
                return null;
            }
            String relativePath = id.getNamespace().equals(Ponderer.MODID)
                    ? id.getPath() + ".nbt"
                    : id.getNamespace() + "/" + id.getPath() + ".nbt";
            return resolvePackScopedPath(packDir, pack, relativePath);
        }
        return resolveStructurePath(getStructureDir(), id);
    }

    public static String displaySceneKey(String id, @javax.annotation.Nullable String pack) {
        if (pack == null || pack.isBlank()) {
            return id;
        }
        return "[" + pack + "] " + id;
    }

    public static boolean saveToServer(MinecraftServer server, String sceneId, @javax.annotation.Nullable String pack, String json) {
        ResourceLocation sceneLoc = ResourceLocation.tryParse(sceneId);
        if (sceneLoc == null) {
            LOGGER.warn("Invalid scene id: {}", sceneId);
            return false;
        }

        Path scenePath = resolveServerScenePath(server, sceneLoc, pack);
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

    public static boolean saveStructureToServer(MinecraftServer server, String structureId, @javax.annotation.Nullable String pack,
            byte[] structureBytes) {
        if (structureId == null || structureId.isBlank() || structureBytes == null) {
            return true;
        }

        ResourceLocation structureLoc = ResourceLocation.tryParse(structureId);
        if (structureLoc == null) {
            LOGGER.warn("Invalid structure id: {}", structureId);
            return false;
        }
        Path structurePath = resolveServerStructurePath(server, structureLoc, pack);
        if (structurePath == null) {
            LOGGER.warn("Rejected unsafe structure path for id {} pack {}", structureId, pack);
            return false;
        }
        try {
            Files.createDirectories(structurePath.getParent());
            Files.write(structurePath, structureBytes);
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to write structure: {}", structurePath, e);
            return false;
        }
    }

    public static List<SyncFileRef> collectServerScriptRefs(MinecraftServer server) {
        ensureServerPackLayoutMigrated(server);

        Path flatRoot = getServerSceneDir(server);
        Path packsRoot = getServerPacksRoot(server);
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

    private static void collectServerSceneRefsFromRoot(Path root, @javax.annotation.Nullable String explicitPack, List<SyncFileRef> entries) {
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

    public static List<SyncFileRef> collectServerStructureRefs(MinecraftServer server) {
        ensureServerPackLayoutMigrated(server);

        Path flatRoot = getServerStructureDir(server);
        Path packsRoot = getServerPacksRoot(server);
        if (!Files.exists(flatRoot) && !Files.exists(packsRoot)) {
            return List.of();
        }

        List<SyncFileRef> entries = new ArrayList<>();
        collectServerStructureRefsFromRoot(flatRoot, null, entries);

        if (!Files.exists(packsRoot)) {
            return entries;
        }

        try (Stream<Path> packDirs = Files.list(packsRoot)) {
            for (Path packDir : packDirs.filter(Files::isDirectory).sorted().toList()) {
                String packId = packDir.getFileName() != null ? packDir.getFileName().toString() : null;
                if (packId == null || packId.isBlank()) {
                    continue;
                }
                collectServerStructureRefsFromRoot(packDir.resolve(STRUCTURE_DIR), packId, entries);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to collect packed server structures", e);
        }
        return entries;
    }

    private static void collectServerStructureRefsFromRoot(Path root, @javax.annotation.Nullable String explicitPack,
            List<SyncFileRef> entries) {
        if (root == null || !Files.exists(root)) {
            return;
        }

        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".nbt"))
                    .filter(path -> !path.startsWith(root.resolve(PACKS_SUBDIR)))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList()) {
                SyncFileRef ref = toServerStructureRef(root, path, ".nbt", explicitPack);
                if (ref != null) {
                    entries.add(ref);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to collect server structures from {}", root, e);
        }
    }

    @javax.annotation.Nullable
    private static SyncFileRef toServerSceneRef(Path root, Path file, @javax.annotation.Nullable String explicitPack) {
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            DslScene scene = GSON.fromJson(json, DslScene.class);
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

    @javax.annotation.Nullable
    static SyncFileRef toServerStructureRef(Path root, Path file, String ext, @javax.annotation.Nullable String explicitPack) {
        Path rel = root.relativize(file);
        if (rel.getNameCount() < 1) {
            return null;
        }

        String pack = explicitPack;
        Path idPath = rel;
        if ((pack == null || pack.isBlank()) && PACKS_SUBDIR.equals(rel.getName(0).toString()) && rel.getNameCount() >= 3) {
            pack = rel.getName(1).toString();
            idPath = rel.subpath(2, rel.getNameCount());
        }

        if (pack != null && !pack.isBlank()) {
            String first = idPath.getName(0).toString();
            String prefix = "[" + pack + "] ";
            if (first.startsWith(prefix)) {
                idPath = idPath.getNameCount() == 1
                        ? Path.of(first.substring(prefix.length()))
                        : Path.of(first.substring(prefix.length()), idPath.subpath(1, idPath.getNameCount()).toString());
            }
        }

        if (idPath.getNameCount() == 0) {
            return null;
        }

        String namespace;
        String path;
        if (idPath.getNameCount() == 1) {
            namespace = Ponderer.MODID;
            path = idPath.getName(0).toString();
        } else {
            namespace = idPath.getName(0).toString();
            path = idPath.subpath(1, idPath.getNameCount()).toString().replace("\\", "/");
        }
        if (path.endsWith(ext)) {
            path = path.substring(0, path.length() - ext.length());
        }
        if (namespace.isBlank() || path.isBlank()) {
            return null;
        }
        return new SyncFileRef(namespace + ":" + path, pack, file);
    }

    @javax.annotation.Nullable
    private static String inferPackName(Path root, Path file) {
        Path rel = root.relativize(file);
        if (rel.getNameCount() >= 2 && PACKS_SUBDIR.equals(rel.getName(0).toString())) {
            return rel.getName(1).toString();
        }
        return null;
    }

    /**
     * Save a DslScene to the local config directory.
     * Routes to pack subdirectory if scene has a pack field.
     *
     * @param scene the DslScene to serialize and save
     * @return true if saved successfully
     */
    @javax.annotation.Nullable
    public static Path resolveLocalScenePath(DslScene scene) {
        if (scene == null || scene.id == null || scene.id.isBlank()) {
            return null;
        }

        ResourceLocation loc = ResourceLocation.tryParse(scene.id);
        if (loc == null) {
            return null;
        }

        Path dir;
        String filename;
        if (scene.pack != null && !scene.pack.isEmpty()) {
            dir = getPackSceneDir(scene.pack);
            if (dir == null) {
                return null;
            }
            filename = "[" + scene.pack + "] " + loc.getPath().replace('/', '_') + ".json";
        } else {
            dir = getSceneDir();
            filename = loc.getPath().replace('/', '_') + ".json";
        }

        Path filePath = SafePaths.resolveFileName(dir, filename);
        if (filePath == null) {
            return null;
        }

        Path existingFile = findExistingFileForScene(scene);
        return existingFile != null ? existingFile : filePath;
    }

    @javax.annotation.Nullable
    public static Path findLocalSceneFile(String sceneId) {
        return findLocalSceneFile(sceneId, null);
    }

    @javax.annotation.Nullable
    public static Path findLocalSceneFile(String sceneId, @javax.annotation.Nullable String pack) {
        if (sceneId == null || sceneId.isBlank()) {
            return null;
        }
        DslScene temp = new DslScene();
        temp.id = sceneId;
        temp.pack = pack;

        if (pack != null && !pack.isBlank()) {
            Path packDir = getPackSceneDir(pack);
            Path existing = packDir == null ? null : findExistingFile(packDir, sceneId);
            return existing != null ? existing : resolveLocalScenePath(temp);
        }

        Path existing = findExistingFile(getSceneDir(), sceneId);
        if (existing != null) {
            return existing;
        }
        ResourceLocation loc = ResourceLocation.tryParse(sceneId);
        if (loc == null) {
            return null;
        }
        return resolveLocalScenePath(temp);
    }

    public static LocalSaveResult saveSceneToLocalDetailed(DslScene scene) {
        LocalSaveResult validationFailure = validateLocalSceneSave(scene);
        if (validationFailure != null) {
            LOGGER.warn(validationFailure.englishMessage());
            return validationFailure;
        }

        Path filePath = resolveLocalScenePath(scene);
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
            sanitizeScene(scene);
            String json = GSON_PRETTY.toJson(scene);
            Files.writeString(filePath, json, StandardCharsets.UTF_8);
            if (scene.pack != null && !scene.pack.isBlank()) {
                deleteDirectoryRecursive(getReadonlyCachePackSceneDir(scene.pack));
                deleteDirectoryRecursive(getReadonlyCachePackStructureDir(scene.pack));
            }
            LOGGER.info("Saved scene {} to {}", scene.id, filePath);
            return LocalSaveResult.success(filePath, "Saved scene '" + scene.id + "' to " + filePath);
        } catch (IOException e) {
            LocalSaveResult failure = mapIoFailure(scene.id, filePath, e);
            LOGGER.error(failure.englishMessage(), e);
            return failure;
        }
    }

    public static boolean saveSceneToLocal(DslScene scene) {
        return saveSceneToLocalDetailed(scene).isSuccess();
    }

    /**
     * Find existing file for a scene considering its pack context.
     */
    @javax.annotation.Nullable
    private static Path findExistingFileForScene(DslScene scene) {
        if (scene.pack != null && !scene.pack.isEmpty()) {
            Path packDir = getPackSceneDir(scene.pack);
            return packDir == null ? null : findExistingFile(packDir, scene.id);
        }
        return findExistingFile(getSceneDir(), scene.id);
    }

    /**
     * Find the existing JSON file that contains a scene with the given id.
     */
    private static Path findExistingFile(Path dir, String sceneId) {
        if (!Files.exists(dir)) return null;
        try (Stream<Path> paths = Files.walk(dir)) {
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
        Path packsDir = getPacksRoot();
        if (!Files.exists(packsDir)) return null;
        try (Stream<Path> packDirs = Files.list(packsDir)) {
            for (Path packDir : packDirs.filter(Files::isDirectory).toList()) {
                Path searchRoot = getPackContentSearchRoot(packDir, SCRIPT_DIR);
                if (searchRoot == null || !Files.exists(searchRoot)) {
                    continue;
                }
                Path found = findExistingFile(searchRoot, sceneId);
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
        if (searchDir == null) {
            return null;
        }
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

    private static void ensureLocalPackLayoutMigrated() {
        migrateLegacyPackTypeRoot(getLegacyPackSceneRoot(), getPacksRoot(), SCRIPT_DIR, "local script");
        migrateLegacyPackTypeRoot(getLegacyPackStructureRoot(), getPacksRoot(), STRUCTURE_DIR, "local structure");
    }

    private static void ensureServerPackLayoutMigrated(MinecraftServer server) {
        migrateLegacyPackTypeRoot(getLegacyServerPackSceneRoot(server), getServerPacksRoot(server), SCRIPT_DIR, "server script");
        migrateLegacyPackTypeRoot(getLegacyServerPackStructureRoot(server), getServerPacksRoot(server), STRUCTURE_DIR, "server structure");
    }

    private static void migrateLegacyPackTypeRoot(Path legacyRoot, Path packsRoot, String typeDir, String label) {
        if (legacyRoot == null || !Files.exists(legacyRoot)) {
            return;
        }

        try (Stream<Path> packDirs = Files.list(legacyRoot)) {
            for (Path legacyPackDir : packDirs.filter(Files::isDirectory).toList()) {
                String packId = legacyPackDir.getFileName() != null ? legacyPackDir.getFileName().toString() : null;
                if (packId == null || packId.isBlank()) {
                    continue;
                }

                Path packDir = SafePaths.resolveFileName(packsRoot, packId);
                Path targetDir = resolvePackTypeDir(packDir, typeDir);
                if (packDir == null || targetDir == null) {
                    LOGGER.warn("Rejected unsafe legacy {} pack path for {}", label, legacyPackDir);
                    continue;
                }

                migrateLegacyPackDirectory(legacyPackDir, targetDir, label);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to inspect legacy {} pack root {}", label, legacyRoot, e);
        }

        deleteEmptyDirectories(legacyRoot);
    }

    private static void migrateLegacyPackDirectory(Path legacyDir, Path targetDir, String label) {
        try {
            Path targetParent = targetDir.getParent();
            if (targetParent == null) {
                LOGGER.warn("Missing parent for migrated {} pack directory {}", label, targetDir);
                return;
            }

            Files.createDirectories(targetParent);
            if (!Files.exists(targetDir)) {
                Files.move(legacyDir, targetDir);
                LOGGER.info("Migrated legacy {} pack directory: {} -> {}", label, legacyDir, targetDir);
                return;
            }

            try (Stream<Path> paths = Files.walk(legacyDir)) {
                for (Path path : paths.sorted(Comparator.comparingInt(Path::getNameCount)).toList()) {
                    Path relative = legacyDir.relativize(path);
                    if (relative.getNameCount() == 0) {
                        continue;
                    }

                    Path targetPath = SafePaths.resolveRelativePath(targetDir, relative.toString().replace("\\", "/"));
                    if (targetPath == null) {
                        LOGGER.warn("Rejected unsafe migrated {} relative path {} in {}", label, relative, legacyDir);
                        continue;
                    }

                    if (Files.isDirectory(path)) {
                        Files.createDirectories(targetPath);
                        continue;
                    }

                    Files.createDirectories(targetPath.getParent());
                    if (!Files.exists(targetPath)) {
                        Files.move(path, targetPath);
                        continue;
                    }

                    if (filesHaveSameContent(path, targetPath)) {
                        Files.deleteIfExists(path);
                        continue;
                    }

                    LOGGER.warn("Keeping conflicting legacy {} file {} because target already exists at {}", label, path, targetPath);
                }
            }

            deleteEmptyDirectories(legacyDir);
            LOGGER.info("Merged legacy {} pack directory into {}", label, targetDir);
        } catch (IOException e) {
            LOGGER.warn("Failed to migrate legacy {} pack directory {}", label, legacyDir, e);
        }
    }

    private static boolean filesHaveSameContent(Path left, Path right) {
        try {
            return Files.size(left) == Files.size(right) && Files.mismatch(left, right) == -1;
        } catch (IOException e) {
            return false;
        }
    }

    private static void deleteEmptyDirectories(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (!Files.isDirectory(path)) {
                    continue;
                }

                try (Stream<Path> children = Files.list(path)) {
                    if (children.findAny().isEmpty()) {
                        Files.deleteIfExists(path);
                    }
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    /** Names of all built-in basic structures bundled in the jar. */
    private static final String[] BASIC_STRUCTURES = {
        "basic", "basic_xs", "basic_s", "basic_l", "basic_xl", "basic_xxl"
    };

    public static void extractDefaultsIfNeeded() {
        Path baseDir = PondererServices.PLATFORM.getConfigDir().resolve(BASE_DIR);
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

        // Ensure all basic structures are present on every startup
        for (String name : BASIC_STRUCTURES) {
            extractResource("data/ponderer/default_structures/" + name + ".nbt",
                structureDir.resolve(name + ".nbt"));
        }

        if (!Files.exists(marker)) {
            try {
                extractResource("data/ponderer/default_scripts/ponderer_example.json", scriptsDir.resolve("ponderer_example.json"));
                Files.writeString(marker, "initialized", StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOGGER.warn("Failed to write initialization marker", e);
            }
        }
    }

    /**
     * Ensure a built-in structure file exists in the local structures folder.
     * If the file is missing but a built-in resource exists in the jar, copy it.
     * Returns true if the file now exists locally.
     */
    public static boolean ensureBuiltinStructure(String path) {
        Path target = SafePaths.resolveRelativePath(getStructureDir(), path + ".nbt");
        if (target == null) {
            LOGGER.warn("Rejected unsafe built-in structure path: {}", path);
            return false;
        }
        if (Files.exists(target)) return true;
        try (InputStream in = openBuiltinStructure(path)) {
            if (in == null) return false;
            Files.createDirectories(target.getParent());
            Files.copy(in, target);
            LOGGER.info("Copied built-in structure to local: {}", target);
            return true;
        } catch (IOException e) {
            LOGGER.warn("Failed to copy built-in structure: {}", target, e);
            return false;
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
        List<String> segments = SafePaths.splitValidatedRelativePath(path);
        if (segments == null) {
            return null;
        }
        String resourcePath = "data/ponderer/default_structures/" + String.join("/", segments) + ".nbt";
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
        ensureLocalPackLayoutMigrated();

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
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                .filter(path -> !path.startsWith(dir.resolve(PACKS_SUBDIR)))
                .sorted(Comparator.comparing(Path::toString))
                .forEach(path -> loadSceneFile(path, loaded));
        } catch (IOException e) {
            LOGGER.error("Failed to list scene directory: {}", dir, e);
        }

        // 2. Load imported local packs (_packs/{PackName}/scripts)
        loadPackScenesFromRoot(getPacksRoot(), loaded);

        // 3. Load readonly cached source packs after imported locals.
        loadPackScenesFromRoot(getReadonlyCachePacksRoot(), loaded);

        SceneRuntime.setScenes(loaded);
        LOGGER.info("Loaded {} ponderer scene(s) from {}", loaded.size(), dir);
        return loaded.size();
    }

    private static void loadPackScenesFromRoot(Path packsDir, List<DslScene> loaded) {
        if (!Files.exists(packsDir)) {
            return;
        }

        try (Stream<Path> packDirs = Files.list(packsDir)) {
            for (Path packDir : packDirs.filter(Files::isDirectory).sorted().toList()) {
                Path searchRoot = getPackContentSearchRoot(packDir, SCRIPT_DIR);
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
     * Ensure every scene segment starts with a valid scene-start step.
     * If the first meaningful step is neither show_structure nor show_interface,
     * prepend show_structure + idle(20).
     * This prevents crashes when operations like hide_section come first.
     */
    public static void sanitizeScene(DslScene scene) {
        if (scene.editable == null) {
            scene.editable = Boolean.TRUE;
        }
        if (scene.scenes != null) {
            for (DslScene.SceneSegment seg : scene.scenes) {
                ensureFirstStepIsShowStructure(seg);
            }
        }
    }

    private static void ensureFirstStepIsShowStructure(DslScene.SceneSegment seg) {
        if (seg.steps == null || seg.steps.isEmpty()) return;
        for (DslScene.DslStep step : seg.steps) {
            if (step == null || step.type == null) continue;
            if ("show_structure".equalsIgnoreCase(step.type) || "show_interface".equalsIgnoreCase(step.type)) {
                return; // already correct
            }
            break; // first meaningful step is not a valid scene-start step
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

    @javax.annotation.Nullable
    private static LocalSaveResult validateLocalSceneSave(DslScene scene) {
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

    private static String buildLocalSceneFileName(@javax.annotation.Nullable String packName, ResourceLocation loc) {
        String prefix = packName != null && !packName.isBlank() ? "[" + packName + "] " : "";
        return prefix + loc.getPath().replace('/', '_') + ".json";
    }

    @javax.annotation.Nullable
    private static String findInvalidSceneIdCharacter(String sceneId) {
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

    private static LocalSaveResult toPackNameFailure(String sceneId, String packName, SafePaths.FileNameValidationError error) {
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

    private static LocalSaveResult toFileNameFailure(String sceneId, String fileName, SafePaths.FileNameValidationError error) {
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

    private static LocalSaveResult mapIoFailure(String sceneId, Path filePath, IOException e) {
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

    // ===== Pack Export/Import Methods =====

    /**
     * Pack all scenes and structures into a Ponderer resource pack (zip).
     * File will be created at: resourcepacks/[Ponderer] {name}.zip
     * After export: reorganizes files into _packs/{name}/ and reloads.
     */
    public static PackExportResult packScenesAndStructuresDetailed(String name, String version, String author) {
        PackExportResult nameFailure = validatePackExportName(name);
        if (nameFailure != null) {
            LOGGER.warn(nameFailure.englishMessage());
            return nameFailure;
        }

        String normalizedName = name.trim();
        Path resourcepacksDir = PondererServices.PLATFORM.getGameDir().resolve("resourcepacks");
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

    public static boolean packScenesAndStructures(String name, String version, String author) {
        return packScenesAndStructuresDetailed(name, version, author).isSuccess();
    }

    /**
     * Pack selected scenes and their structures into a Ponderer resource pack (zip).
     * Only includes scenes whose IDs are in the selectedSceneIds set.
     * File will be created at: resourcepacks/[Ponderer] {name}.zip
     * After export: reorganizes exported files into _packs/{name}/ and reloads.
     */
    public static PackExportResult packSelectedScenesAndStructuresDetailed(String name, String version, String author, Set<String> selectedSceneIds) {
        if (selectedSceneIds == null || selectedSceneIds.isEmpty()) {
            return packScenesAndStructuresDetailed(name, version, author);
        }

        PackExportResult nameFailure = validatePackExportName(name);
        if (nameFailure != null) {
            LOGGER.warn(nameFailure.englishMessage());
            return nameFailure;
        }

        String normalizedName = name.trim();
        Path resourcepacksDir = PondererServices.PLATFORM.getGameDir().resolve("resourcepacks");
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
                        DslScene scene = GSON.fromJson(rawJson, DslScene.class);
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

    public static boolean packSelectedScenesAndStructures(String name, String version, String author, Set<String> selectedSceneIds) {
        return packSelectedScenesAndStructuresDetailed(name, version, author, selectedSceneIds).isSuccess();
    }

    @javax.annotation.Nullable
    private static PackExportResult validatePackExportName(String name) {
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

    @javax.annotation.Nullable
    private static Path resolvePackExportOutputPath(Path resourcepacksDir, String packName) {
        String filename = "[Ponderer] " + packName + ".zip";
        return SafePaths.resolveFileName(resourcepacksDir, filename);
    }

    private static PackExportResult mapPackExportIoFailure(String packName, @javax.annotation.Nullable Path outputPath, IOException e) {
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

    /**
     * Collect all script files from flat directory and pack subdirectories.
     */
    private static List<Path> collectAllScriptFiles() {
        List<Path> result = new ArrayList<>();
        collectFilesWithExtension(getSceneDir(), ".json", result);

        Path packsDir = getPacksRoot();
        if (!Files.exists(packsDir)) {
            return result;
        }

        try (Stream<Path> packDirs = Files.list(packsDir)) {
            for (Path packDir : packDirs.filter(Files::isDirectory).toList()) {
                collectFilesWithExtension(getPackContentSearchRoot(packDir, SCRIPT_DIR), ".json", result);
            }
        } catch (IOException ignored) {}

        return result;
    }

    private static void collectFilesWithExtension(@javax.annotation.Nullable Path root, String extension, List<Path> result) {
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
        List<Path> allStructureFiles = new ArrayList<>();
        collectFilesWithExtension(getStructureDir(), ".nbt", allStructureFiles);

        Path packsDir = getPacksRoot();
        if (Files.exists(packsDir)) {
            try (Stream<Path> packDirs = Files.list(packsDir)) {
                for (Path packDir : packDirs.filter(Files::isDirectory).toList()) {
                    collectFilesWithExtension(getPackContentSearchRoot(packDir, STRUCTURE_DIR), ".nbt", allStructureFiles);
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

    public static List<PonderPackInfo> scanAvailableSourcePacks() {
        Path resourcepacksDir = PondererServices.PLATFORM.getGameDir().resolve("resourcepacks");
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

    public static PackImportResult importPackFromResourcePack(Path zipPath) {
        if (zipPath == null || !Files.exists(zipPath)) {
            PackImportResult failure = PackImportResult.failure(
                "Pack file not found: " + zipPath,
                "ponderer.ui.import.failed",
                "Pack file not found");
            LOGGER.warn(failure.englishMessage());
            return failure;
        }

        ensureLocalPackLayoutMigrated();
        PackStateStore.load();
        PonderPackInfo info = PonderPackInfo.fromZip(zipPath);
        if (info == null) {
            PackImportResult failure = PackImportResult.failure(
                "Invalid Ponderer pack: " + zipPath,
                "ponderer.ui.import.failed",
                "Invalid Ponderer pack");
            LOGGER.warn(failure.englishMessage());
            return failure;
        }

        PackStateStore.ImportedPackState existing = PackStateStore.getImportedPack(info.name);
        if (PackStateStore.isImported(info.name)) {
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

        Path packScriptsDir = getPackSceneDir(info.name);
        Path packStructuresDir = getPackStructureDir(info.name);
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
            PackStateStore.markImported(info);
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

    private static int extractPackContents(Path zipPath, PonderPackInfo info, Path packScriptsDir, Path packStructuresDir) throws IOException {
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
                    Path targetPath = resolvePackScopedPath(packScriptsDir, info.name, fileName);
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
                    Path targetPath = resolvePackScopedPath(packStructuresDir, info.name, fileName);
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

    private static void rebuildReadonlyCacheForPack(Path zipPath, PonderPackInfo info) throws IOException {
        Path packScriptsDir = getReadonlyCachePackSceneDir(info.name);
        Path packStructuresDir = getReadonlyCachePackStructureDir(info.name);
        if (packScriptsDir == null || packStructuresDir == null) {
            LOGGER.warn("Rejected unsafe readonly cache directories for pack {}", info.name);
            return;
        }

        extractPackContents(zipPath, info, packScriptsDir, packStructuresDir);
    }

    private static void clearReadonlyCache() {
        deleteDirectoryRecursive(getReadonlyCacheRoot());
    }

    private static void deleteDirectoryRecursive(Path dir) {
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

    /**
     * After export, reorganize files on disk:
     * Move exported files into _packs/{packName}/ with proper [PackName] prefix and pack field.
     * Then reload scripts.
     */
    private static void reorganizeFilesForPack(String packName, List<Path> exportedFiles) {
        Path packScriptsDir = getPackSceneDir(packName);
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
                DslScene scene = GSON.fromJson(rawJson, DslScene.class);
                if (scene == null) continue;
                scene.pack = packName;
                String updatedJson = GSON_PRETTY.toJson(scene);

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

    /**
     * Move structure files referenced by pack scenes into the pack structure directory.
     */
    private static void reorganizeStructuresForPack(String packName) {
        Path packStructuresDir = getPackStructureDir(packName);
        Path packScriptsDir = getPackSceneDir(packName);
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
        Path structuresDir = getStructureDir();
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

    private static void collectStructureReferences(String sceneJson, Set<String> structures) {
        try {
            collectStructureReferences(GSON.fromJson(sceneJson, DslScene.class), structures);
        } catch (Exception e) {
            LOGGER.warn("Failed to collect structure references from scene", e);
        }
    }

    static void collectStructureReferences(@javax.annotation.Nullable DslScene scene, Set<String> structures) {
        if (scene == null || structures == null) {
            return;
        }

        List<String> pool = getStructurePool(scene);
        for (String ref : pool) {
            addStructureReference(ref, structures);
        }

        if (scene.scenes == null) {
            return;
        }

        for (DslScene.SceneSegment segment : scene.scenes) {
            if (segment == null || segment.steps == null) {
                continue;
            }
            for (DslScene.DslStep step : segment.steps) {
                if (step == null || step.structure == null || step.structure.isBlank()) {
                    continue;
                }
                addStructureReference(resolveStructureReference(pool, step.structure), structures);
            }
        }
    }

    private static List<String> getStructurePool(DslScene scene) {
        if (scene.structures != null && !scene.structures.isEmpty()) {
            return scene.structures;
        }
        if (scene.structure != null && !scene.structure.isBlank()) {
            return List.of(scene.structure);
        }
        return List.of();
    }

    @javax.annotation.Nullable
    private static String resolveStructureReference(List<String> pool, String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        String trimmed = ref.trim();
        Integer parsed = tryParseInt(trimmed);
        if (parsed == null) {
            return trimmed;
        }

        int index = -1;
        if (parsed >= 1 && parsed <= pool.size()) {
            index = parsed - 1;
        } else if (parsed >= 0 && parsed < pool.size()) {
            index = parsed;
        }
        return index >= 0 ? pool.get(index) : null;
    }

    private static void addStructureReference(@javax.annotation.Nullable String ref, Set<String> structures) {
        if (ref == null || ref.isBlank()) {
            return;
        }
        String trimmed = ref.trim();
        if (!trimmed.matches("\\d+")) {
            structures.add(trimmed);
        }
    }

    @javax.annotation.Nullable
    private static Integer tryParseInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return null;
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
                    Path targetPath = resolvePackScopedPath(packScriptsDir, info.name, fileName);
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
                    Path targetPath = resolvePackScopedPath(packStructuresDir, info.name, fileName);
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

    /**
     * Check if at least one extracted script file from a pack still exists on disk.
     */
    private static boolean packScriptFilesExist(String packName) {
        Path packDir = getPackSceneDir(packName);
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
        ensureLocalPackLayoutMigrated();
        PackStateStore.load();
        clearReadonlyCache();
        List<PackUpdateInfo> updates = new ArrayList<>();
        Set<String> presentSourcePacks = new HashSet<>();

        for (PonderPackInfo info : scanAvailableSourcePacks()) {
            presentSourcePacks.add(info.name);
            PackStateStore.updateCurrentSource(info);

            if (PackStateStore.isImported(info.name)) {
                PackStateStore.ImportedPackState imported = PackStateStore.getImportedPack(info.name);
                if (imported != null && PackStateStore.shouldNotifySourceUpdate(info.name, info.version)) {
                    updates.add(new PackUpdateInfo(info.name, imported.importedVersion, info.version, 0, 0));
                    PackStateStore.markNotified(info.name, info.version);
                }
                continue;
            }

            try {
                rebuildReadonlyCacheForPack(info.sourcePath, info);
            } catch (IOException e) {
                LOGGER.warn("Failed to build readonly cache for pack {}", info.sourcePath, e);
            }
        }

        PackStateStore.clearMissingSources(presentSourcePacks);
        return new AutoLoadResult(List.of(), updates);
    }

    /**
     * Remove orphaned packs from registry (unregister without deleting zip).
     */
    public static void removeOrphanedPacks(List<String> displayNames) {
        for (String name : displayNames) {
            PackStateStore.removeImportedPack(name);
            LOGGER.info("Removed imported pack state: {}", name);
        }
    }
}
