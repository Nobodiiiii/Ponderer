package com.nododiiiii.ponderer.application;

import com.google.gson.*;
import com.nododiiiii.ponderer.application.api.SaveOutcome;
import com.nododiiiii.ponderer.exporter.*;
import com.nododiiiii.ponderer.runtime.api.*;
import com.nododiiiii.ponderer.scene.*;
import com.nododiiiii.ponderer.workspace.api.WorkspaceResults.LocalSaveResult;
import com.nododiiiii.ponderer.platform.PondererServices;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Local source-project bindings persist; scene replacements and source baselines do not.
 * All entry points run on the client thread. Opening a project never activates uncompiled source.
 */
public final class JavaSceneWorkspace {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<String, Path> PROJECTS = new LinkedHashMap<>();
    private static final Map<String, Source> SOURCES = new LinkedHashMap<>();
    private static long revision;
    private static boolean loaded;
    private JavaSceneWorkspace() {}
    private static final class Source {
        final Path root, file;
        final String relative;
        String accepted;
        long revision = ++JavaSceneWorkspace.revision;
        DslScene published;
        Source(Path root, Path file, String relative) throws IOException {
            this.root = root; this.file = file; this.relative = relative;
            accepted = Files.readString(file);
        }
    }
    private static Path preferences() {
        return PondererServices.PLATFORM.getConfigDir().resolve("ponderer/java-projects.properties");
    }
    private static void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.isRegularFile(preferences())) return;
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(preferences())) {
            properties.load(reader);
            properties.forEach((key, value) -> PROJECTS.put(key.toString(), Path.of(value.toString()).toAbsolutePath().normalize()));
        } catch (Exception error) { com.mojang.logging.LogUtils.getLogger().warn("Cannot read Java project bindings", error); }
    }
    private static JavaModuleExportManifest manifest(Path root) throws IOException {
        try (Reader reader = Files.newBufferedReader(root.resolve(".ponderer-export/manifest.json"))) {
            var manifest = GSON.fromJson(reader, JavaModuleExportManifest.class);
            if (manifest == null || !JavaModuleExportRequest.Mode.JAVA_LIB.name().equals(manifest.mode)
                || manifest.modId == null || manifest.scenes == null)
                throw new IOException("The target must contain a Java + Lib export manifest");
            return manifest;
        }
    }
    public static void bind(Path project) throws IOException {
        load();
        Path root = project.toRealPath();
        var manifest = manifest(root);
        Path previous = PROJECTS.put(manifest.modId, root);
        try {
            Properties properties = new Properties();
            PROJECTS.forEach((namespace, path) -> properties.setProperty(namespace, path.toString()));
            StringWriter text = new StringWriter();
            properties.store(text, "Ponderer local Java source projects; no scene content is stored here");
            writeFiles(new LinkedHashMap<>(Map.of(preferences(), text.toString())), null, null);
        } catch (IOException error) {
            if (previous == null) PROJECTS.remove(manifest.modId); else PROJECTS.put(manifest.modId, previous);
            throw error;
        }
        if (!root.equals(previous)) SOURCES.entrySet().removeIf(entry -> entry.getKey().startsWith(manifest.modId + ":"));
        // Capture a baseline, but never import source automatically on startup or binding.
        for (String id : EditableScenes.sourceIds()) if (id.startsWith(manifest.modId + ":")) source(id);
    }
    private static Source source(DslScene scene) throws IOException {
        return scene.runtimeSource == null ? null : source(scene.runtimeSource);
    }
    private static Source source(String sceneId) throws IOException {
        load();
        Source existing = SOURCES.get(sceneId);
        if (existing != null) return existing;
        var id = net.minecraft.resources.ResourceLocation.parse(sceneId);
        Path root = PROJECTS.get(id.getNamespace());
        if (root == null) return null;
        root = root.toRealPath();
        var manifest = manifest(root);
        var entry = manifest.scenes.values().stream().filter(value -> value.sceneId != null
            && (value.sceneId.equals(sceneId)
                || value.sceneId.substring(value.sceneId.indexOf(':') + 1).equals(id.getPath()))).findFirst().orElse(null);
        if (entry == null || entry.javaFiles == null || entry.javaFiles.size() != 1)
            throw new IOException("No unique generated Java source for " + sceneId);
        String relative = entry.javaFiles.getFirst();
        Path javaRoot = root.resolve("src/main/java").toRealPath();
        Path file = root.resolve(relative).toRealPath();
        if (!relative.endsWith(".java") || !file.startsWith(javaRoot)) throw new IOException("Java source is outside the target project");
        Source next = new Source(root, file, relative);
        SOURCES.put(sceneId, next);
        return next;
    }
    public static boolean writable(DslScene scene) {
        if (scene.runtimeSource == null) return true;
        try { Source source = source(scene); return source != null && Files.isWritable(source.file); }
        catch (Exception error) { return false; }
    }
    public static void track(DslScene draft) {
        if (draft.runtimeSource == null) return;
        try { Source source = source(draft); if (source != null && draft.javaSourceRevision == 0) draft.javaSourceRevision = source.revision; }
        catch (Exception error) { com.mojang.logging.LogUtils.getLogger().warn("Cannot track Java scene {}", draft.id, error); }
    }
    public static DslScene updated(DslScene draft) {
        Source source = SOURCES.get(draft.runtimeSource);
        return source != null && source.published != null
            && draft.javaSourceRevision != source.revision ? SceneCodec.snapshot(source.published) : null;
    }
    public static SaveOutcome save(DslScene scene) {
        LocalSaveResult result;
        try {
            Source source = source(scene);
            if (source == null) throw new IOException("Bind the target Java + Lib project first");
            String current = Files.readString(source.file);
            if (!current.equals(source.accepted) || scene.javaSourceRevision != 0 && scene.javaSourceRevision != source.revision)
                throw new IOException("Java source changed externally; reload and reopen this scene before saving");
            JavaSceneSource.read(current, scene.id);
            EditableScenes.validate(scene);
            var bundle = SceneTextBundle.extract(scene);
            DslScene document = bundle.document();
            String rendered = JavaSceneSource.write(current, document);
            var writes = new LinkedHashMap<Path, String>();
            var locales = readTexts(source.root, scene.id);
            bundle.locales().forEach((locale, values) -> locales.computeIfAbsent(locale, ignored -> new TreeMap<>()).putAll(values));
            Path textRoot = textRoot(source.root, scene.id);
            locales.forEach((locale, values) -> writes.put(textRoot.resolve(locale + ".json"), GSON.toJson(values) + "\n"));
            writes.put(source.file, rendered);
            var manifest = manifest(source.root);
            if (manifest.generatedSources == null) manifest.generatedSources = new LinkedHashMap<>();
            manifest.generatedSources.put(source.relative, rendered);
            for (var entry : manifest.scenes.values()) if (entry.javaFiles != null && entry.javaFiles.contains(source.relative)) {
                entry.runtimeFeatures = SceneRuntimeRequirements.features(document).stream().sorted().toList();
                if (entry.resourceFiles == null) entry.resourceFiles = new ArrayList<>();
                for (String locale : locales.keySet()) {
                    String relative = source.root.relativize(textRoot.resolve(locale + ".json")).toString().replace('\\', '/');
                    if (!entry.resourceFiles.contains(relative)) entry.resourceFiles.add(relative);
                }
            }
            writes.put(source.root.resolve(".ponderer-export/manifest.json"), GSON.toJson(manifest) + "\n");
            // The owned feature declaration is rebuilt from all scenes, including unselected ones.
            var features = new TreeSet<>(Set.of("core", "render_cache"));
            manifest.scenes.values().forEach(entry -> {
                if (entry.runtimeFeatures != null && !entry.runtimeFeatures.isEmpty()) features.addAll(entry.runtimeFeatures);
                else features.addAll(Set.of("view", "text", "nbt_bindings", "interfaces"));
            });
            Path declaration = source.root.resolve("src/main/resources/META-INF/pondererlib.features/ponderer_export.properties");
            Properties requirements = new Properties();
            if (Files.isRegularFile(declaration)) try (Reader reader = Files.newBufferedReader(declaration)) { requirements.load(reader); }
            requirements.setProperty("features", String.join(",", features));
            StringWriter featureText = new StringWriter();
            requirements.store(featureText, "Ponderer Java + Lib scene requirements");
            writes.put(declaration, featureText.toString());
            writeFiles(writes, source.file, current);
            source.accepted = rendered;
            result = LocalSaveResult.success(source.file, "Saved Java scene to " + source.file);
            try {
                document.javaSourceRevision = revision + 1;
                publish(document, locales);
                scene.javaSourceRevision = source.revision = ++revision;
                source.published = SceneCodec.snapshot(document);
            }
            catch (RuntimeException error) { return new SaveOutcome(result, SceneRefreshCoordinator.version(), error); }
        } catch (Exception error) {
            result = LocalSaveResult.failure(error.getMessage(), "ponderer.ui.save_error.java_scene", error.getMessage());
        }
        return new SaveOutcome(result, SceneRefreshCoordinator.version(), null);
    }
    /** Explicit reload only: the V menu and reload commands share this entry point. */
    public static int reload() {
        int changed = 0;
        var failures = new ArrayList<String>();
        for (String id : EditableScenes.sourceIds()) {
            try {
                Source source = source(id);
                if (source == null) continue;
                String text = Files.readString(source.file);
                DslScene document = JavaSceneSource.read(text, id);
                document.runtimeSource = id;
                document.javaSourceRevision = revision + 1;
                EditableScenes.validate(document);
                publish(document, readTexts(source.root, document.id));
                source.accepted = text;
                source.revision = ++revision;
                source.published = SceneCodec.snapshot(document);
                changed++;
            } catch (Exception error) {
                com.mojang.logging.LogUtils.getLogger().warn("Java source reload failed for {}; keeping the previous scene", id, error);
                failures.add(id + ": " + error.getMessage());
            }
        }
        if (!failures.isEmpty()) throw new IllegalStateException(String.join("\n", failures));
        return changed;
    }
    private static void publish(DslScene scene, Map<String, Map<String, String>> locales) {
        var previous = EditableScenes.overrides();
        var texts = SceneTexts.sessionTexts(scene.id);
        var client = net.minecraft.client.Minecraft.getInstance();
        int openSegment = -1;
        if (client.screen instanceof net.createmod.ponder.foundation.ui.PonderUI ui) {
            var match = com.nododiiiii.ponderer.application.api.RuntimeAccess.findBySceneId(ui.getActiveScene().getId(), -1);
            if (match != null && scene.id.equals(match.scene().runtimeSource)) openSegment = match.sceneIndex();
        }
        try {
            SceneTexts.setSessionTexts(scene.id, locales);
            EditableScenes.putOverride(scene);
            SceneRefreshCoordinator.reloadPonderIndex();
        } catch (RuntimeException error) {
            EditableScenes.setOverrides(previous);
            SceneTexts.setSessionTexts(scene.id, texts);
            try { SceneRefreshCoordinator.reloadPonderIndex(); } catch (RuntimeException recovery) { error.addSuppressed(recovery); }
            throw error;
        }
        // An already open player owns compiled scenes; replace that screen as well as the registry.
        if (openSegment >= 0) {
            if (scene.scenes == null || scene.scenes.isEmpty()
                || !com.nododiiiii.ponderer.application.ui.PonderScreenNavigation.openPonderUIForScene(
                    scene, Math.min(openSegment, scene.scenes.size() - 1))) client.setScreen(null);
        }
    }
    private static Path textRoot(Path root, String id) throws IOException {
        var location = net.minecraft.resources.ResourceLocation.parse(id);
        Path resources = root.resolve("src/main/resources").toRealPath();
        Path path = resources.resolve("assets/" + location.getNamespace() + "/ponderer/texts/" + location.getPath()).normalize();
        if (!path.startsWith(resources)) throw new IOException("Text resources are outside the target project");
        return path;
    }
    private static Map<String, Map<String, String>> readTexts(Path root, String id) throws IOException {
        var locales = new TreeMap<String, Map<String, String>>();
        Path directory = textRoot(root, id);
        if (!Files.isDirectory(directory)) return locales;
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().matches("[a-z0-9_]+\\.json")).toList()) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
                    var values = new TreeMap<String, String>();
                    object.entrySet().forEach(entry -> {
                        LocalizedText.ref(id, entry.getKey());
                        if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString())
                            throw new IllegalArgumentException("Text must be a string: " + path + "#" + entry.getKey());
                        values.put(entry.getKey(), entry.getValue().getAsString());
                    });
                    locales.put(path.getFileName().toString().replace(".json", ""), values);
                }
            }
        }
        return locales;
    }
    private static void writeFiles(LinkedHashMap<Path, String> writes, Path source, String expected) throws IOException {
        var previous = new LinkedHashMap<Path, String>();
        try {
            for (var entry : writes.entrySet()) {
                Path path = entry.getKey();
                if (path.equals(source) && !Files.readString(path).equals(expected)) throw new IOException("Java source changed during save");
                previous.put(path, Files.exists(path) ? Files.readString(path) : null);
                replace(path, entry.getValue());
            }
        } catch (IOException error) {
            var restored = new ArrayList<>(previous.entrySet());
            Collections.reverse(restored);
            for (var entry : restored) try {
                if (entry.getValue() == null) Files.deleteIfExists(entry.getKey());
                else replace(entry.getKey(), entry.getValue());
            } catch (IOException rollback) { error.addSuppressed(rollback); }
            throw error;
        }
    }
    private static void replace(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".ponderer-java-", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    public static void close() {
        SOURCES.clear(); PROJECTS.clear(); loaded = false;
        EditableScenes.clearOverrides();
        SceneTexts.clearSessionTexts();
    }
}
