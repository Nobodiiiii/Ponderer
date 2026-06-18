package com.nododiiiii.ponderer.ponder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.network.SyncResponsePayload;
import com.nododiiiii.ponderer.network.UploadScenePayload;
import com.nododiiiii.ponderer.util.SafePaths;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class RemoteWorkspaceService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter())
        .create();

    public static final String KIND_SCENE = "scene";
    public static final String KIND_STRUCTURE = "structure";
    public static final String KIND_PACK = "pack";
    public static final String KIND_ITEM_SCENES = "item_scenes";

    private static final String HISTORY_DIR = "remote_history";
    private static final String LOCAL_SCOPE = "_local";
    private static final String PACK_SCOPE = "_packs";
    private static final String SCENE_HISTORY_DIR = "scenes";
    private static final String STRUCTURE_HISTORY_DIR = "structures";

    private RemoteWorkspaceService() {
    }

    public record CatalogEntry(String kind, String id, @Nullable String pack, String title, String summary,
                               String hash, int revision, long size, long updatedAt, String updatedBy,
                               int dependencyCount, int refCount) {
    }

    public record HistoryEntry(String kind, String id, @Nullable String pack, int revision, String action,
                               String actor, long createdAt, String hash, long size) {
    }

    public record CatalogSnapshot(List<CatalogEntry> scenes, List<CatalogEntry> structures,
                                  List<CatalogEntry> packs, boolean canPull, boolean canUpload,
                                  boolean canManage) {
    }

    public record PullBundle(List<SyncResponsePayload.FileEntry> scripts,
                             List<SyncResponsePayload.FileEntry> structures, String message) {
    }

    public record OperationResult(boolean success, String message, boolean refreshCatalog, String hash) {
        public static OperationResult ok(String message, boolean refreshCatalog) {
            return new OperationResult(true, message, refreshCatalog, "");
        }

        public static OperationResult ok(String message, boolean refreshCatalog, String hash) {
            return new OperationResult(true, message, refreshCatalog, hash == null ? "" : hash);
        }

        public static OperationResult fail(String message) {
            return new OperationResult(false, message, false, "");
        }
    }

    private static final class HistoryMeta {
        String kind;
        String id;
        @Nullable String pack;
        int revision;
        String action;
        String actor;
        long createdAt;
        String hash;
        long size;
    }

    public static CatalogSnapshot catalog(MinecraftServer server, ServerPlayer viewer) {
        List<SceneStore.SyncFileRef> sceneRefs = SceneStore.collectServerScriptRefs(server);
        List<SceneStore.SyncFileRef> structureRefs = SceneStore.collectServerStructureRefs(server);
        Map<String, Integer> structureRefCounts = countStructureReferences(server, sceneRefs);
        Map<String, Integer> packSceneCounts = new LinkedHashMap<>();
        Map<String, Integer> packStructureCounts = new LinkedHashMap<>();

        List<CatalogEntry> scenes = new ArrayList<>();
        for (SceneStore.SyncFileRef ref : sceneRefs) {
            CatalogEntry entry = sceneEntry(server, ref);
            scenes.add(entry);
            if (entry.pack() != null && !entry.pack().isBlank()) {
                packSceneCounts.merge(entry.pack(), 1, Integer::sum);
            }
        }

        List<CatalogEntry> structures = new ArrayList<>();
        for (SceneStore.SyncFileRef ref : structureRefs) {
            CatalogEntry entry = structureEntry(server, ref, structureRefCounts.getOrDefault(refKey(ref.id(), ref.pack()), 0));
            structures.add(entry);
            if (entry.pack() != null && !entry.pack().isBlank()) {
                packStructureCounts.merge(entry.pack(), 1, Integer::sum);
            }
        }

        Set<String> packNames = new HashSet<>();
        packNames.addAll(packSceneCounts.keySet());
        packNames.addAll(packStructureCounts.keySet());
        List<CatalogEntry> packs = new ArrayList<>();
        for (String pack : packNames) {
            int sceneCount = packSceneCounts.getOrDefault(pack, 0);
            int structureCount = packStructureCounts.getOrDefault(pack, 0);
            packs.add(new CatalogEntry(KIND_PACK, pack, null, pack,
                sceneCount + " scenes, " + structureCount + " structures",
                "", 0, 0, 0, "", sceneCount, structureCount));
        }

        Comparator<CatalogEntry> sorter = Comparator
            .comparing((CatalogEntry entry) -> entry.pack() == null ? "" : entry.pack(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(CatalogEntry::id, String.CASE_INSENSITIVE_ORDER);
        scenes.sort(sorter);
        structures.sort(sorter);
        packs.sort(Comparator.comparing(CatalogEntry::id, String.CASE_INSENSITIVE_ORDER));

        return new CatalogSnapshot(
            List.copyOf(scenes),
            List.copyOf(structures),
            List.copyOf(packs),
            UploadPermissions.canPull(viewer),
            UploadPermissions.canUpload(viewer),
            UploadPermissions.canManage(viewer));
    }

    public static PullBundle collectPullBundle(MinecraftServer server, String kind, String id,
                                               @Nullable String pack, boolean includeDependencies) {
        List<SyncResponsePayload.FileEntry> scripts = new ArrayList<>();
        List<SyncResponsePayload.FileEntry> structures = new ArrayList<>();

        if (KIND_PACK.equals(kind)) {
            for (SceneStore.SyncFileRef ref : SceneStore.collectServerScriptRefs(server)) {
                if (samePack(packOrId(id, pack), ref.pack())) {
                    addScriptEntry(ref, scripts);
                }
            }
            for (SceneStore.SyncFileRef ref : SceneStore.collectServerStructureRefs(server)) {
                if (samePack(packOrId(id, pack), ref.pack())) {
                    addStructureEntry(ref, structures);
                }
            }
            return new PullBundle(List.copyOf(scripts), List.copyOf(structures), "Pulled remote pack " + packOrId(id, pack));
        }

        if (KIND_ITEM_SCENES.equals(kind)) {
            Set<String> structureKeys = new HashSet<>();
            int matchedScenes = 0;
            for (SceneStore.SyncFileRef sceneRef : SceneStore.collectServerScriptRefs(server)) {
                DslScene scene = readScene(sceneRef.path());
                if (!sceneMatchesItem(scene, id)) {
                    continue;
                }
                matchedScenes++;
                addScriptEntry(sceneRef, scripts);
                if (includeDependencies) {
                    for (SceneStore.SyncFileRef structureRef : collectSceneStructureRefs(server, sceneRef)) {
                        if (structureKeys.add(refKey(structureRef.id(), structureRef.pack()))) {
                            addStructureEntry(structureRef, structures);
                        }
                    }
                }
            }
            if (matchedScenes == 0) {
                return new PullBundle(List.of(), List.of(), "No remote scenes found for item " + id);
            }
            return new PullBundle(List.copyOf(scripts), List.copyOf(structures),
                "Pulled " + matchedScenes + " remote scene(s) for item " + id);
        }

        if (KIND_SCENE.equals(kind)) {
            SceneStore.SyncFileRef sceneRef = findSceneRef(server, id, pack);
            if (sceneRef == null) {
                return new PullBundle(List.of(), List.of(), "Remote scene not found: " + displayKey(id, pack));
            }
            addScriptEntry(sceneRef, scripts);
            if (includeDependencies) {
                for (SceneStore.SyncFileRef structureRef : collectSceneStructureRefs(server, sceneRef)) {
                    addStructureEntry(structureRef, structures);
                }
            }
            return new PullBundle(List.copyOf(scripts), List.copyOf(structures), "Pulled remote scene " + displayKey(id, pack));
        }

        if (KIND_STRUCTURE.equals(kind)) {
            SceneStore.SyncFileRef structureRef = findStructureRef(server, id, pack);
            if (structureRef == null) {
                return new PullBundle(List.of(), List.of(), "Remote structure not found: " + displayKey(id, pack));
            }
            addStructureEntry(structureRef, structures);
            return new PullBundle(List.of(), List.copyOf(structures), "Pulled remote structure " + displayKey(id, pack));
        }

        return new PullBundle(List.of(), List.of(), "Unknown remote resource type: " + kind);
    }

    public static OperationResult uploadScene(ServerPlayer actor, UploadScenePayload payload) {
        if (actor == null) {
            return OperationResult.fail("No server player is available.");
        }
        boolean ok = SceneStore.saveToServer(actor.server, payload.sceneId(), payload.pack(), payload.json());
        if (ok) {
            snapshotCurrent(actor.server, KIND_SCENE, payload.sceneId(), payload.pack(), "upload", actorName(actor));
        }

        if (ok && payload.structures() != null) {
            for (UploadScenePayload.StructureEntry entry : payload.structures()) {
                if (entry == null || entry.id() == null || entry.id().isBlank() || entry.bytes() == null) {
                    continue;
                }
                ok = SceneStore.saveStructureToServer(actor.server, entry.id(), entry.pack(), entry.bytes()) && ok;
                if (ok) {
                    snapshotCurrent(actor.server, KIND_STRUCTURE, entry.id(), entry.pack(), "upload", actorName(actor));
                }
            }
        }

        if (!ok) {
            return OperationResult.fail("Failed to upload " + displayKey(payload.sceneId(), payload.pack()));
        }
        return OperationResult.ok(
            "Uploaded " + displayKey(payload.sceneId(), payload.pack()),
            true,
            currentHash(actor.server, KIND_SCENE, payload.sceneId(), payload.pack()));
    }

    public static OperationResult delete(MinecraftServer server, ServerPlayer actor, String kind, String id,
                                         @Nullable String pack) {
        if (!UploadPermissions.canManage(actor)) {
            return OperationResult.fail("Only Ponderer admins can delete remote resources.");
        }
        if (KIND_SCENE.equals(kind)) {
            Path path = resolveCurrentPath(server, kind, id, pack);
            if (path == null || !Files.exists(path)) {
                return OperationResult.fail("Remote scene not found: " + displayKey(id, pack));
            }
            snapshotCurrent(server, kind, id, pack, "delete", actorName(actor));
            return deletePath(path, "Deleted remote scene " + displayKey(id, pack));
        }

        if (KIND_STRUCTURE.equals(kind)) {
            int refCount = countStructureReferences(server, SceneStore.collectServerScriptRefs(server))
                .getOrDefault(refKey(id, pack), 0);
            if (refCount > 0) {
                return OperationResult.fail("Cannot delete " + displayKey(id, pack) + ": referenced by " + refCount + " scene(s).");
            }
            Path path = resolveCurrentPath(server, kind, id, pack);
            if (path == null || !Files.exists(path)) {
                return OperationResult.fail("Remote structure not found: " + displayKey(id, pack));
            }
            snapshotCurrent(server, kind, id, pack, "delete", actorName(actor));
            return deletePath(path, "Deleted remote structure " + displayKey(id, pack));
        }

        return OperationResult.fail("Unsupported delete target: " + kind);
    }

    public static List<HistoryEntry> history(MinecraftServer server, String kind, String id, @Nullable String pack) {
        Path dir = historyResourceDir(server, kind, id, pack);
        if (dir == null || !Files.exists(dir)) {
            return List.of();
        }
        List<HistoryEntry> entries = new ArrayList<>();
        try (Stream<Path> paths = Files.list(dir)) {
            for (Path metaPath : paths.filter(path -> path.getFileName().toString().endsWith(".meta.json")).toList()) {
                HistoryMeta meta = readHistoryMeta(metaPath);
                if (meta != null) {
                    entries.add(new HistoryEntry(meta.kind, meta.id, meta.pack, meta.revision, meta.action,
                        meta.actor, meta.createdAt, meta.hash, meta.size));
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to list remote history for {} {}", kind, displayKey(id, pack), e);
        }
        entries.sort(Comparator.comparingInt(HistoryEntry::revision).reversed());
        return List.copyOf(entries);
    }

    public static OperationResult rollback(MinecraftServer server, ServerPlayer actor, String kind, String id,
                                           @Nullable String pack, int revision) {
        if (!UploadPermissions.canManage(actor)) {
            return OperationResult.fail("Only Ponderer admins can roll back remote resources.");
        }
        Path historyFile = historyContentPath(server, kind, id, pack, revision);
        Path currentPath = resolveCurrentPath(server, kind, id, pack);
        if (historyFile == null || currentPath == null || !Files.exists(historyFile)) {
            return OperationResult.fail("Remote history revision not found: " + revision);
        }
        try {
            Files.createDirectories(currentPath.getParent());
            Files.copy(historyFile, currentPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            snapshotCurrent(server, kind, id, pack, "rollback:" + revision, actorName(actor));
            return OperationResult.ok("Rolled back " + displayKey(id, pack) + " to revision " + revision, true);
        } catch (Exception e) {
            LOGGER.warn("Failed to roll back remote resource {} {}", kind, displayKey(id, pack), e);
            return OperationResult.fail("Failed to roll back " + displayKey(id, pack));
        }
    }

    @Nullable
    private static SceneStore.SyncFileRef findSceneRef(MinecraftServer server, String id, @Nullable String pack) {
        for (SceneStore.SyncFileRef ref : SceneStore.collectServerScriptRefs(server)) {
            if (ref.id().equals(id) && samePack(ref.pack(), pack)) {
                return ref;
            }
        }
        return null;
    }

    @Nullable
    private static SceneStore.SyncFileRef findStructureRef(MinecraftServer server, String id, @Nullable String pack) {
        for (SceneStore.SyncFileRef ref : SceneStore.collectServerStructureRefs(server)) {
            if (ref.id().equals(id) && samePack(ref.pack(), pack)) {
                return ref;
            }
        }
        return null;
    }

    private static List<SceneStore.SyncFileRef> collectSceneStructureRefs(MinecraftServer server,
                                                                          SceneStore.SyncFileRef sceneRef) {
        DslScene scene = readScene(sceneRef.path());
        if (scene == null) {
            return List.of();
        }
        Set<String> refs = new HashSet<>();
        SceneStore.collectStructureReferences(scene, refs);
        if (refs.isEmpty()) {
            return List.of();
        }

        Map<String, SceneStore.SyncFileRef> available = new HashMap<>();
        for (SceneStore.SyncFileRef ref : SceneStore.collectServerStructureRefs(server)) {
            available.put(refKey(ref.id(), ref.pack()), ref);
        }

        List<SceneStore.SyncFileRef> result = new ArrayList<>();
        for (String ref : refs) {
            ResourceLocation id = parseStructureId(ref);
            if (id == null) {
                continue;
            }
            SceneStore.SyncFileRef match = available.get(refKey(id.toString(), sceneRef.pack()));
            if (match == null) {
                match = available.get(refKey(id.toString(), null));
            }
            if (match != null) {
                result.add(match);
            }
        }
        return result;
    }

    private static void addScriptEntry(SceneStore.SyncFileRef ref, List<SyncResponsePayload.FileEntry> target) {
        try {
            target.add(new SyncResponsePayload.FileEntry(ref.id(), ref.pack(), Files.readAllBytes(ref.path())));
        } catch (Exception e) {
            LOGGER.warn("Failed to read remote scene {}", displayKey(ref.id(), ref.pack()), e);
        }
    }

    private static void addStructureEntry(SceneStore.SyncFileRef ref, List<SyncResponsePayload.FileEntry> target) {
        try {
            target.add(new SyncResponsePayload.FileEntry(ref.id(), ref.pack(), Files.readAllBytes(ref.path())));
        } catch (Exception e) {
            LOGGER.warn("Failed to read remote structure {}", displayKey(ref.id(), ref.pack()), e);
        }
    }

    private static CatalogEntry sceneEntry(MinecraftServer server, SceneStore.SyncFileRef ref) {
        DslScene scene = readScene(ref.path());
        String title = scene != null && scene.title != null ? scene.title.resolve("en_us") : "";
        String summary = "";
        int dependencyCount = 0;
        if (scene != null) {
            List<String> items = scene.items == null ? List.of() : scene.items;
            summary = items.isEmpty() ? "No bound items" : String.join(", ", items);
            Set<String> structures = new HashSet<>();
            SceneStore.collectStructureReferences(scene, structures);
            dependencyCount = structures.size();
        }
        return fileEntry(server, KIND_SCENE, ref.id(), ref.pack(), title, summary, ref.path(), dependencyCount, 0);
    }

    private static CatalogEntry structureEntry(MinecraftServer server, SceneStore.SyncFileRef ref, int refCount) {
        return fileEntry(server, KIND_STRUCTURE, ref.id(), ref.pack(), "", "Referenced by " + refCount + " scene(s)",
            ref.path(), 0, refCount);
    }

    private static CatalogEntry fileEntry(MinecraftServer server, String kind, String id, @Nullable String pack,
                                          String title, String summary, Path path, int dependencyCount, int refCount) {
        long size = 0;
        long updatedAt = 0;
        String hash = "";
        try {
            byte[] bytes = Files.readAllBytes(path);
            size = bytes.length;
            hash = SyncMeta.sha256(bytes);
            updatedAt = Files.getLastModifiedTime(path).toMillis();
        } catch (Exception e) {
            LOGGER.warn("Failed to inspect remote resource {}", path, e);
        }
        HistoryEntry latest = history(server, kind, id, pack).stream().findFirst().orElse(null);
        int revision = latest == null ? 0 : latest.revision();
        String actor = latest == null ? "" : latest.actor();
        return new CatalogEntry(kind, id, pack, title == null ? "" : title, summary == null ? "" : summary,
            hash, revision, size, updatedAt, actor, dependencyCount, refCount);
    }

    private static Map<String, Integer> countStructureReferences(MinecraftServer server, List<SceneStore.SyncFileRef> scenes) {
        Map<String, Integer> counts = new HashMap<>();
        for (SceneStore.SyncFileRef sceneRef : scenes) {
            DslScene scene = readScene(sceneRef.path());
            if (scene == null) {
                continue;
            }
            Set<String> refs = new HashSet<>();
            SceneStore.collectStructureReferences(scene, refs);
            for (String ref : refs) {
                ResourceLocation loc = parseStructureId(ref);
                if (loc == null) {
                    continue;
                }
                counts.merge(refKey(loc.toString(), sceneRef.pack()), 1, Integer::sum);
                counts.merge(refKey(loc.toString(), null), 0, Integer::sum);
            }
        }
        return counts;
    }

    @Nullable
    private static DslScene readScene(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, DslScene.class);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse remote scene {}", path, e);
            return null;
        }
    }

    private static boolean sceneMatchesItem(@Nullable DslScene scene, String itemId) {
        if (scene == null || scene.items == null || itemId == null || itemId.isBlank()) {
            return false;
        }
        for (String rawItem : scene.items) {
            String boundItem = cleanItemId(rawItem);
            if (itemId.equals(boundItem)) {
                return true;
            }
        }
        return false;
    }

    private static String cleanItemId(@Nullable String rawItem) {
        if (rawItem == null) {
            return "";
        }
        String itemId = rawItem.trim();
        int nbtStart = itemId.indexOf('{');
        if (nbtStart >= 0) {
            itemId = itemId.substring(0, nbtStart).trim();
        }
        return itemId;
    }

    @Nullable
    private static ResourceLocation parseStructureId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.matches("\\d+")) {
            return null;
        }
        if (trimmed.contains(":")) {
            return ResourceLocation.tryParse(trimmed);
        }
        return ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, trimmed);
    }

    private static OperationResult deletePath(Path path, String message) {
        try {
            Files.deleteIfExists(path);
            return OperationResult.ok(message, true);
        } catch (Exception e) {
            LOGGER.warn("Failed to delete remote resource {}", path, e);
            return OperationResult.fail("Failed to delete remote resource.");
        }
    }

    private static void snapshotCurrent(MinecraftServer server, String kind, String id, @Nullable String pack,
                                        String action, String actor) {
        Path source = resolveCurrentPath(server, kind, id, pack);
        if (source == null || !Files.exists(source)) {
            return;
        }
        Path dir = historyResourceDir(server, kind, id, pack);
        if (dir == null) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(source);
            int revision = nextRevision(dir);
            Files.createDirectories(dir);
            Path contentPath = historyContentPath(dir, kind, revision);
            if (contentPath == null) {
                return;
            }
            Files.write(contentPath, bytes);

            HistoryMeta meta = new HistoryMeta();
            meta.kind = kind;
            meta.id = id;
            meta.pack = pack;
            meta.revision = revision;
            meta.action = action;
            meta.actor = actor;
            meta.createdAt = Instant.now().toEpochMilli();
            meta.hash = SyncMeta.sha256(bytes);
            meta.size = bytes.length;

            Path metaPath = historyMetaPath(dir, revision);
            if (metaPath != null) {
                Files.writeString(metaPath, GSON.toJson(meta), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to write remote history for {} {}", kind, displayKey(id, pack), e);
        }
    }

    private static int nextRevision(Path dir) {
        int max = 0;
        if (!Files.exists(dir)) {
            return 1;
        }
        try (Stream<Path> paths = Files.list(dir)) {
            for (Path path : paths.toList()) {
                String name = path.getFileName().toString();
                int dot = name.indexOf('.');
                if (dot <= 0) {
                    continue;
                }
                try {
                    max = Math.max(max, Integer.parseInt(name.substring(0, dot)));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return max + 1;
    }

    @Nullable
    private static HistoryMeta readHistoryMeta(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, HistoryMeta.class);
        } catch (Exception e) {
            LOGGER.warn("Failed to read remote history metadata {}", path, e);
            return null;
        }
    }

    @Nullable
    private static Path resolveCurrentPath(MinecraftServer server, String kind, String id, @Nullable String pack) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        if (loc == null) {
            return null;
        }
        if (KIND_SCENE.equals(kind)) {
            return SceneStore.resolveServerScenePath(server, loc, pack);
        }
        if (KIND_STRUCTURE.equals(kind)) {
            return SceneStore.resolveServerStructurePath(server, loc, pack);
        }
        return null;
    }

    private static String currentHash(MinecraftServer server, String kind, String id, @Nullable String pack) {
        Path path = resolveCurrentPath(server, kind, id, pack);
        if (path == null || !Files.exists(path)) {
            return "";
        }
        try {
            return SyncMeta.sha256(Files.readAllBytes(path));
        } catch (Exception e) {
            return "";
        }
    }

    @Nullable
    private static Path historyResourceDir(MinecraftServer server, String kind, String id, @Nullable String pack) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        if (loc == null) {
            return null;
        }
        String typeDir = KIND_SCENE.equals(kind) ? SCENE_HISTORY_DIR : STRUCTURE_HISTORY_DIR;
        List<String> segments = new ArrayList<>();
        segments.add(HISTORY_DIR);
        segments.add(typeDir);
        if (pack == null || pack.isBlank()) {
            segments.add(LOCAL_SCOPE);
        } else {
            if (SafePaths.validateWindowsFileNameSegment(pack) == null) {
                return null;
            }
            segments.add(PACK_SCOPE);
            segments.add(pack);
        }
        segments.add(loc.getNamespace());
        for (String segment : loc.getPath().split("/")) {
            if (segment.isBlank()) {
                return null;
            }
            segments.add(segment);
        }
        return SafePaths.resolveRelativePath(server.getWorldPath(LevelResource.ROOT).resolve("ponderer"), segments);
    }

    @Nullable
    private static Path historyContentPath(MinecraftServer server, String kind, String id, @Nullable String pack,
                                           int revision) {
        Path dir = historyResourceDir(server, kind, id, pack);
        return dir == null ? null : historyContentPath(dir, kind, revision);
    }

    @Nullable
    private static Path historyContentPath(Path dir, String kind, int revision) {
        String ext = KIND_SCENE.equals(kind) ? ".json" : ".nbt";
        return SafePaths.resolveFileName(dir, String.format(Locale.ROOT, "%06d%s", revision, ext));
    }

    @Nullable
    private static Path historyMetaPath(Path dir, int revision) {
        return SafePaths.resolveFileName(dir, String.format(Locale.ROOT, "%06d.meta.json", revision));
    }

    private static String actorName(ServerPlayer player) {
        return player == null ? "server" : player.getGameProfile().getName();
    }

    private static String refKey(String id, @Nullable String pack) {
        return (pack == null || pack.isBlank() ? "" : "[" + pack + "] ") + id;
    }

    private static String displayKey(String id, @Nullable String pack) {
        return SceneStore.displaySceneKey(id, pack);
    }

    private static boolean samePack(@Nullable String a, @Nullable String b) {
        String left = a == null ? "" : a;
        String right = b == null ? "" : b;
        return left.equals(right);
    }

    private static String packOrId(String id, @Nullable String pack) {
        return pack == null || pack.isBlank() ? id : pack;
    }
}
