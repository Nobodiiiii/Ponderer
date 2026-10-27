package com.nododiiiii.ponderer.workspace.local;

import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.util.SafePaths;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import com.nododiiiii.ponderer.workspace.api.*;
import com.nododiiiii.ponderer.workspace.api.WorkspaceResults.*;

/** Internal file implementation. Construct through WorkspaceFiles. */
public final class LocalStructureRepository implements StructureRepository {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BASE_DIR = "ponderer";
    private static final String SCRIPT_DIR = "scripts";
    private static final String STRUCTURE_DIR = "structures";
    private static final String PACKS_SUBDIR = "_packs";
    private static final String SAVE_ERROR_KEY_PREFIX = "ponderer.ui.save_error.";
    private static final String[] BASIC_STRUCTURES = {"basic", "basic_xs", "basic_s", "basic_l", "basic_xl", "basic_xxl"};
    private final WorkspaceFiles workspace;
    LocalStructureRepository(WorkspaceFiles workspace) { this.workspace = workspace; }

    public boolean saveStructureToServer(MinecraftServer server, String structureId, @javax.annotation.Nullable String pack,
            byte[] structureBytes) {
        if (structureId == null || structureId.isBlank() || structureBytes == null) {
            return true;
        }

        ResourceLocation structureLoc = ResourceLocation.tryParse(structureId);
        if (structureLoc == null) {
            LOGGER.warn("Invalid structure id: {}", structureId);
            return false;
        }
        Path structurePath = workspace.layout.resolveServerStructurePath(server, structureLoc, pack);
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

    public List<SyncFileRef> collectServerStructureRefs(MinecraftServer server) {
        workspace.layout.ensureServerPackLayoutMigrated(server);

        Path flatRoot = workspace.layout.getServerStructureDir(server);
        Path packsRoot = workspace.layout.getServerPacksRoot(server);
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

    void collectServerStructureRefsFromRoot(Path root, @javax.annotation.Nullable String explicitPack,
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

    public SyncFileRef toServerStructureRef(Path root, Path file, String ext, @javax.annotation.Nullable String explicitPack) {
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
            namespace = WorkspaceLayout.DEFAULT_NAMESPACE;
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

    public void extractDefaultsIfNeeded() {
        Path baseDir = workspace.configDir.resolve(BASE_DIR);
        Path marker = baseDir.resolve(".initialized");

        Path scriptsDir = workspace.layout.getSceneDir();
        Path structureDir = workspace.layout.getStructureDir();
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

    public boolean ensureBuiltinStructure(String path) {
        Path target = SafePaths.resolveRelativePath(workspace.layout.getStructureDir(), path + ".nbt");
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

    void extractResource(String resourcePath, Path target) {
        if (Files.exists(target)) return;
        try (InputStream in = LocalStructureRepository.class.getClassLoader().getResourceAsStream(resourcePath)) {
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

    public InputStream openBuiltinStructure(String path) {
        List<String> segments = SafePaths.splitValidatedRelativePath(path);
        if (segments == null) {
            return null;
        }
        String resourcePath = "data/ponderer/default_structures/" + String.join("/", segments) + ".nbt";
        return LocalStructureRepository.class.getClassLoader().getResourceAsStream(resourcePath);
    }

    public boolean isBuiltinStructureName(String name) {
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
}
