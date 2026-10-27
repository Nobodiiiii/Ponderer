package com.nododiiiii.ponderer.workspace.local;

import com.nododiiiii.ponderer.workspace.api.WorkspaceLayoutView;
import com.nododiiiii.ponderer.scene.DslScene;
import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.util.SafePaths;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import com.nododiiiii.ponderer.workspace.api.*;
import com.nododiiiii.ponderer.workspace.api.WorkspaceResults.*;

/** Internal file implementation. Construct through WorkspaceFiles. */
public final class WorkspaceLayout implements WorkspaceLayoutView {
    static final String DEFAULT_NAMESPACE = "ponderer";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BASE_DIR = "ponderer";
    private static final String SCRIPT_DIR = "scripts";
    private static final String STRUCTURE_DIR = "structures";
    private static final String PACKS_SUBDIR = "_packs";
    private static final String SAVE_ERROR_KEY_PREFIX = "ponderer.ui.save_error.";
    private final WorkspaceFiles workspace;
    WorkspaceLayout(WorkspaceFiles workspace) { this.workspace = workspace; }

    public Path getSceneDir() {
        return workspace.configDir.resolve(BASE_DIR).resolve(SCRIPT_DIR);
    }

    public Path getStructureDir() {
        return workspace.configDir.resolve(BASE_DIR).resolve(STRUCTURE_DIR);
    }

    public Path getReadonlyCacheRoot() {
        return workspace.configDir.resolve(BASE_DIR).resolve(".cache").resolve("readonly");
    }

    public Path getPacksRoot() {
        return workspace.configDir.resolve(BASE_DIR).resolve(PACKS_SUBDIR);
    }

    Path getReadonlyCachePacksRoot() {
        return getReadonlyCacheRoot().resolve(PACKS_SUBDIR);
    }

    Path getLegacyPackSceneRoot() {
        return getSceneDir().resolve(PACKS_SUBDIR);
    }

    Path getLegacyPackStructureRoot() {
        return getStructureDir().resolve(PACKS_SUBDIR);
    }

    Path getServerPacksRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(BASE_DIR).resolve(PACKS_SUBDIR);
    }

    Path getLegacyServerPackSceneRoot(MinecraftServer server) {
        return getServerSceneDir(server).resolve(PACKS_SUBDIR);
    }

    Path getLegacyServerPackStructureRoot(MinecraftServer server) {
        return getServerStructureDir(server).resolve(PACKS_SUBDIR);
    }

    Path resolvePackTypeDir(@javax.annotation.Nullable Path packDir, String typeDir) {
        return packDir == null ? null : SafePaths.resolveFileName(packDir, typeDir);
    }

    public Path getReadonlyCachePackDir(String packName) {
        return SafePaths.resolveFileName(getReadonlyCachePacksRoot(), packName);
    }

    public Path getReadonlyCachePackSceneDir(String packName) {
        return resolvePackTypeDir(getReadonlyCachePackDir(packName), SCRIPT_DIR);
    }

    public Path getReadonlyCachePackStructureDir(String packName) {
        return resolvePackTypeDir(getReadonlyCachePackDir(packName), STRUCTURE_DIR);
    }

    public Path getPackDir(String packName) {
        return SafePaths.resolveFileName(getPacksRoot(), packName);
    }

    public Path getPackSceneDir(String packName) {
        return resolvePackTypeDir(getPackDir(packName), SCRIPT_DIR);
    }

    public Path getPackStructureDir(String packName) {
        return resolvePackTypeDir(getPackDir(packName), STRUCTURE_DIR);
    }

    Path getLegacyPackSceneDir(String packName) {
        return SafePaths.resolveFileName(getLegacyPackSceneRoot(), packName);
    }

    Path getLegacyPackStructureDir(String packName) {
        return SafePaths.resolveFileName(getLegacyPackStructureRoot(), packName);
    }

    Path getServerPackDir(MinecraftServer server, String packName) {
        return SafePaths.resolveFileName(getServerPacksRoot(server), packName);
    }

    public Path getServerPackSceneDir(MinecraftServer server, String packName) {
        return resolvePackTypeDir(getServerPackDir(server, packName), SCRIPT_DIR);
    }

    public Path getServerPackStructureDir(MinecraftServer server, String packName) {
        return resolvePackTypeDir(getServerPackDir(server, packName), STRUCTURE_DIR);
    }

    public Path getStructurePath(String path) {
        return SafePaths.resolveRelativePath(getStructureDir(), path + ".nbt");
    }

    public Path getStructurePath(ResourceLocation id) {
        return resolveStructurePath(getStructureDir(), id);
    }

    public Path resolveStructurePath(String path, @javax.annotation.Nullable String packName) {
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

    Path getPackContentSearchRoot(Path packDir, String typeDir) {
        if (packDir == null) {
            return null;
        }

        Path typedRoot = packDir.resolve(typeDir);
        return Files.isDirectory(typedRoot) ? typedRoot : packDir;
    }

    Path findStructureInPackRoots(Path packsDir, String path) {
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

                        String defaultNamespaceCandidate = candidate.startsWith(DEFAULT_NAMESPACE + "/")
                            ? candidate.substring((DEFAULT_NAMESPACE + "/").length())
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

    public Path getServerSceneDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(BASE_DIR).resolve(SCRIPT_DIR);
    }

    public Path getServerStructureDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(BASE_DIR).resolve(STRUCTURE_DIR);
    }

    Path resolveScenePath(Path root, ResourceLocation id) {
        return SafePaths.resolveNamespacedPath(root, id, DEFAULT_NAMESPACE, ".json");
    }

    Path resolveStructurePath(Path root, ResourceLocation id) {
        return SafePaths.resolveNamespacedPath(root, id, DEFAULT_NAMESPACE, ".nbt");
    }

    Path resolveServerNamespacedPath(Path root, ResourceLocation id, String extension) {
        return SafePaths.resolveRelativePath(root, id.getNamespace() + "/" + id.getPath() + extension);
    }

    Path resolvePackScopedPath(Path packDir, String packName, String relativePath) {
        List<String> segments = SafePaths.splitValidatedRelativePath(relativePath);
        if (packDir == null || segments == null || segments.isEmpty()) {
            return null;
        }
        List<String> targetSegments = new ArrayList<>(segments);
        targetSegments.set(0, "[" + packName + "] " + targetSegments.get(0));
        return SafePaths.resolveRelativePath(packDir, targetSegments);
    }

    public Path resolveServerScenePath(MinecraftServer server, ResourceLocation id, @javax.annotation.Nullable String pack) {
        if (pack != null && !pack.isBlank()) {
            ensureServerPackLayoutMigrated(server);
            Path packDir = getServerPackSceneDir(server, pack);
            return packDir == null ? null : resolvePackScopedPath(packDir, pack, id.getNamespace() + "/" + id.getPath() + ".json");
        }
        return resolveServerNamespacedPath(getServerSceneDir(server), id, ".json");
    }

    public Path resolveServerStructurePath(MinecraftServer server, ResourceLocation id,
            @javax.annotation.Nullable String pack) {
        if (pack != null && !pack.isBlank()) {
            ensureServerPackLayoutMigrated(server);
            Path packDir = getServerPackStructureDir(server, pack);
            return packDir == null ? null : resolvePackScopedPath(packDir, pack, id.getNamespace() + "/" + id.getPath() + ".nbt");
        }
        return resolveServerNamespacedPath(getServerStructureDir(server), id, ".nbt");
    }

    public Path resolveLocalSyncStructurePath(ResourceLocation id, @javax.annotation.Nullable String pack) {
        if (pack != null && !pack.isBlank()) {
            Path packDir = getPackStructureDir(pack);
            if (packDir == null) {
                return null;
            }
            String relativePath = id.getNamespace().equals(DEFAULT_NAMESPACE)
                    ? id.getPath() + ".nbt"
                    : id.getNamespace() + "/" + id.getPath() + ".nbt";
            return resolvePackScopedPath(packDir, pack, relativePath);
        }
        return resolveStructurePath(getStructureDir(), id);
    }

    public Path resolveLocalScenePath(DslScene scene) {
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

        Path existingFile = workspace.scenes.findExistingFileForScene(scene);
        return existingFile != null ? existingFile : filePath;
    }

    void ensureLocalPackLayoutMigrated() {
        migrateLegacyPackTypeRoot(getLegacyPackSceneRoot(), getPacksRoot(), SCRIPT_DIR, "local script");
        migrateLegacyPackTypeRoot(getLegacyPackStructureRoot(), getPacksRoot(), STRUCTURE_DIR, "local structure");
    }

    void ensureServerPackLayoutMigrated(MinecraftServer server) {
        migrateLegacyPackTypeRoot(getLegacyServerPackSceneRoot(server), getServerPacksRoot(server), SCRIPT_DIR, "server script");
        migrateLegacyPackTypeRoot(getLegacyServerPackStructureRoot(server), getServerPacksRoot(server), STRUCTURE_DIR, "server structure");
    }

    void migrateLegacyPackTypeRoot(Path legacyRoot, Path packsRoot, String typeDir, String label) {
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

    void migrateLegacyPackDirectory(Path legacyDir, Path targetDir, String label) {
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

    boolean filesHaveSameContent(Path left, Path right) {
        try {
            return Files.size(left) == Files.size(right) && Files.mismatch(left, right) == -1;
        } catch (IOException e) {
            return false;
        }
    }

    void deleteEmptyDirectories(Path root) {
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
}
