package com.nododiiiii.ponderer.workspace.local;

import com.nododiiiii.ponderer.workspace.api.PonderPackInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Stores readonly/import state for source packs and imported local copies.
 */
public final class PackStateStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String STATE_FILE = ".pack_state.json";
    private static final String LEGACY_REGISTRY_FILE = ".ponderer_registry.json";

    private final Map<String, ImportedPackState> IMPORTS = new LinkedHashMap<>();
    private final Path STATE_PATH;
    private final Path LEGACY_REGISTRY_PATH;

    private final WorkspaceFiles workspace;
    PackStateStore(WorkspaceFiles workspace) {
        this.workspace = workspace;
        STATE_PATH = workspace.configDir.resolve("ponderer").resolve(STATE_FILE);
        LEGACY_REGISTRY_PATH = workspace.configDir.resolve("ponderer").resolve(LEGACY_REGISTRY_FILE);
    }

    public static final class ImportedPackState {
        public String packId;
        public String displayName;
        public String sourceFile;
        public String importedVersion;
        public String currentSourceVersion;
        public String lastNotifiedSourceVersion;
        public String importedAt;

        ImportedPackState copy() {
            ImportedPackState copy = new ImportedPackState();
            copy.packId = packId;
            copy.displayName = displayName;
            copy.sourceFile = sourceFile;
            copy.importedVersion = importedVersion;
            copy.currentSourceVersion = currentSourceVersion;
            copy.lastNotifiedSourceVersion = lastNotifiedSourceVersion;
            copy.importedAt = importedAt;
            return copy;
        }
    }

    public void load() {
        IMPORTS.clear();

        if (Files.exists(STATE_PATH)) {
            try (Reader reader = Files.newBufferedReader(STATE_PATH, StandardCharsets.UTF_8)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root != null && root.has("imports")) {
                    JsonObject imports = root.getAsJsonObject("imports");
                    for (String packId : imports.keySet()) {
                        try {
                            ImportedPackState state = GSON.fromJson(imports.get(packId), ImportedPackState.class);
                            if (state == null) {
                                continue;
                            }
                            if (state.packId == null || state.packId.isBlank()) {
                                state.packId = packId;
                            }
                            if (state.displayName == null || state.displayName.isBlank()) {
                                state.displayName = state.packId;
                            }
                            IMPORTS.put(state.packId, state);
                        } catch (Exception e) {
                            LOGGER.warn("Failed to parse pack state entry: {}", packId, e);
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to load pack state store", e);
            }
        }

        migrateLegacyImportedPacks();
    }

    public void save() {
        try {
            Files.createDirectories(STATE_PATH.getParent());
            JsonObject root = new JsonObject();
            JsonObject imports = new JsonObject();
            for (Map.Entry<String, ImportedPackState> entry : IMPORTS.entrySet()) {
                imports.add(entry.getKey(), GSON.toJsonTree(entry.getValue()));
            }
            root.add("imports", imports);
            Files.writeString(STATE_PATH, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Failed to save pack state store", e);
        }
    }

    public Collection<ImportedPackState> getImportedPacks() {
        return IMPORTS.values().stream().map(ImportedPackState::copy).toList();
    }

    @Nullable
    public ImportedPackState getImportedPack(String packId) {
        ImportedPackState state = IMPORTS.get(packId);
        return state == null ? null : state.copy();
    }

    public boolean hasImportedState(String packId) {
        return IMPORTS.containsKey(packId);
    }

    public boolean isImported(String packId) {
        return IMPORTS.containsKey(packId) && hasLocalSceneCopy(packId);
    }

    public boolean hasLocalSceneCopy(String packId) {
        Path packDir = workspace.layout.getPackSceneDir(packId);
        if (containsJsonFiles(packDir)) {
            return true;
        }

        Path legacyPackDir = workspace.layout.getSceneDir().resolve("_packs").resolve(packId);
        return containsJsonFiles(legacyPackDir);
    }

    private boolean containsJsonFiles(@Nullable Path packDir) {
        if (packDir == null || !Files.exists(packDir)) {
            return false;
        }

        try (Stream<Path> paths = Files.walk(packDir)) {
            return paths.anyMatch(path -> Files.isRegularFile(path)
                && path.getFileName().toString().toLowerCase().endsWith(".json"));
        } catch (IOException e) {
            LOGGER.warn("Failed to inspect imported local pack directory: {}", packDir, e);
            return false;
        }
    }

    public ImportedPackState markImported(PonderPackInfo info) {
        ImportedPackState state = IMPORTS.computeIfAbsent(info.name, ignored -> new ImportedPackState());
        state.packId = info.name;
        state.displayName = info.name;
        state.sourceFile = info.sourcePath != null && info.sourcePath.getFileName() != null
            ? info.sourcePath.getFileName().toString()
            : state.sourceFile;
        state.importedVersion = info.version;
        state.currentSourceVersion = info.version;
        state.importedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
        save();
        return state.copy();
    }

    public void updateCurrentSource(PonderPackInfo info) {
        ImportedPackState state = IMPORTS.get(info.name);
        if (state == null) {
            return;
        }

        boolean changed = false;
        if (!equalsNullable(state.displayName, info.name)) {
            state.displayName = info.name;
            changed = true;
        }
        String sourceFile = info.sourcePath != null && info.sourcePath.getFileName() != null
            ? info.sourcePath.getFileName().toString()
            : null;
        if (!equalsNullable(state.sourceFile, sourceFile)) {
            state.sourceFile = sourceFile;
            changed = true;
        }
        if (!equalsNullable(state.currentSourceVersion, info.version)) {
            state.currentSourceVersion = info.version;
            changed = true;
        }
        if (changed) {
            save();
        }
    }

    public void clearMissingSources(Collection<String> presentPackIds) {
        TreeSet<String> present = new TreeSet<>(presentPackIds);
        boolean changed = false;

        for (ImportedPackState state : IMPORTS.values()) {
            if (state.packId == null || state.packId.isBlank()) {
                continue;
            }
            if (present.contains(state.packId)) {
                continue;
            }
            if (state.currentSourceVersion != null) {
                state.currentSourceVersion = null;
                changed = true;
            }
        }

        if (changed) {
            save();
        }
    }

    public boolean shouldNotifySourceUpdate(String packId, String sourceVersion) {
        ImportedPackState state = IMPORTS.get(packId);
        if (state == null || state.importedVersion == null || state.importedVersion.isBlank()) {
            return false;
        }
        if (sourceVersion == null || sourceVersion.isBlank()) {
            return false;
        }
        if (sourceVersion.equals(state.importedVersion)) {
            return false;
        }
        return !sourceVersion.equals(state.lastNotifiedSourceVersion);
    }

    public void markNotified(String packId, String sourceVersion) {
        ImportedPackState state = IMPORTS.get(packId);
        if (state == null) {
            return;
        }
        if (equalsNullable(state.lastNotifiedSourceVersion, sourceVersion)) {
            return;
        }
        state.lastNotifiedSourceVersion = sourceVersion;
        save();
    }

    public void removeImportedPack(String packId) {
        if (IMPORTS.remove(packId) != null) {
            save();
        }
    }

    private void migrateLegacyImportedPacks() {
        Map<String, LegacyPackEntry> legacyRegistry = loadLegacyRegistry();
        boolean changed = false;

        for (String packId : listLocalImportedPackIds()) {
            ImportedPackState state = IMPORTS.get(packId);
            LegacyPackEntry legacy = legacyRegistry.get(packId);

            if (state == null) {
                state = new ImportedPackState();
                state.packId = packId;
                state.displayName = legacy != null && legacy.name != null && !legacy.name.isBlank() ? legacy.name : packId;
                state.sourceFile = legacy != null ? legacy.sourceFile : null;
                state.importedVersion = legacy != null ? legacy.version : null;
                state.currentSourceVersion = legacy != null ? legacy.version : null;
                state.importedAt = legacy != null ? legacy.loadedAt : null;
                IMPORTS.put(packId, state);
                changed = true;
                continue;
            }

            if ((state.displayName == null || state.displayName.isBlank()) && legacy != null && legacy.name != null && !legacy.name.isBlank()) {
                state.displayName = legacy.name;
                changed = true;
            }
            if ((state.sourceFile == null || state.sourceFile.isBlank()) && legacy != null && legacy.sourceFile != null && !legacy.sourceFile.isBlank()) {
                state.sourceFile = legacy.sourceFile;
                changed = true;
            }
            if ((state.importedVersion == null || state.importedVersion.isBlank()) && legacy != null && legacy.version != null && !legacy.version.isBlank()) {
                state.importedVersion = legacy.version;
                changed = true;
            }
            if ((state.importedAt == null || state.importedAt.isBlank()) && legacy != null && legacy.loadedAt != null && !legacy.loadedAt.isBlank()) {
                state.importedAt = legacy.loadedAt;
                changed = true;
            }
        }

        if (changed) {
            save();
        }
    }

    private Map<String, LegacyPackEntry> loadLegacyRegistry() {
        Map<String, LegacyPackEntry> entries = new LinkedHashMap<>();
        if (!Files.exists(LEGACY_REGISTRY_PATH)) {
            return entries;
        }

        try (Reader reader = Files.newBufferedReader(LEGACY_REGISTRY_PATH, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("packs")) {
                return entries;
            }

            JsonObject packs = root.getAsJsonObject("packs");
            for (String key : packs.keySet()) {
                try {
                    LegacyPackEntry entry = GSON.fromJson(packs.get(key), LegacyPackEntry.class);
                    if (entry == null) {
                        continue;
                    }
                    String packId = entry.name;
                    if ((packId == null || packId.isBlank()) && entry.packPrefix != null
                        && entry.packPrefix.startsWith("[") && entry.packPrefix.endsWith("]")) {
                        packId = entry.packPrefix.substring(1, entry.packPrefix.length() - 1);
                    }
                    if (packId == null || packId.isBlank()) {
                        continue;
                    }
                    if (entry.name == null || entry.name.isBlank()) {
                        entry.name = packId;
                    }
                    entries.put(packId, entry);
                } catch (Exception e) {
                    LOGGER.warn("Failed to parse legacy pack registry entry: {}", key, e);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to read legacy pack registry", e);
        }

        return entries;
    }

    private TreeSet<String> listLocalImportedPackIds() {
        TreeSet<String> packIds = new TreeSet<>();
        collectPackIds(workspace.layout.getPacksRoot(), packIds);
        collectPackIds(workspace.layout.getSceneDir().resolve("_packs"), packIds);
        collectPackIds(workspace.layout.getStructureDir().resolve("_packs"), packIds);
        return packIds;
    }

    private void collectPackIds(Path packsDir, TreeSet<String> packIds) {
        if (!Files.exists(packsDir)) {
            return;
        }

        try (Stream<Path> paths = Files.list(packsDir)) {
            paths.filter(Files::isDirectory)
                .map(path -> path.getFileName() != null ? path.getFileName().toString() : null)
                .filter(name -> name != null && !name.isBlank())
                .forEach(packIds::add);
        } catch (IOException e) {
            LOGGER.warn("Failed to inspect imported pack directory: {}", packsDir, e);
        }
    }

    private boolean equalsNullable(@Nullable String left, @Nullable String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private static final class LegacyPackEntry {
        public String name;
        public String version;
        public String sourceFile;
        public String loadedAt;
        public String packPrefix;
    }
}
