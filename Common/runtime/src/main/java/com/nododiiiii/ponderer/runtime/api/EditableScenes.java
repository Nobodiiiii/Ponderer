package com.nododiiiii.ponderer.runtime.api;

import com.nododiiiii.ponderer.scene.*;
import net.minecraft.resources.ResourceLocation;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Supplier;

/** Client-thread bridge for Java documents. The optional editor supplies persisted overrides, never Java code. */
public final class EditableScenes {
    private static final Map<String, Registration> SOURCES = new LinkedHashMap<>();
    private static final Map<RuntimeEngine, List<java.util.function.Consumer<DslScene>>> CUSTOMIZERS = new IdentityHashMap<>();
    private static Map<String, DslScene> overrides = Map.of();
    private EditableScenes() {}

    /** Install alongside step providers, before registration. Runs on each fresh compiled document before local overrides. */
    public static AutoCloseable customize(RuntimeEngine engine, java.util.function.Consumer<DslScene> customizer) {
        Objects.requireNonNull(customizer);
        var list = CUSTOMIZERS.computeIfAbsent(engine, ignored -> new ArrayList<>());
        list.add(customizer);
        return () -> { list.remove(customizer); if (list.isEmpty()) CUSTOMIZERS.remove(engine); };
    }

    /** One document per registration; close the engine to release all its registrations. */
    public static Registration register(RuntimeEngine engine, Supplier<DslScene> source) {
        DslScene initial = Objects.requireNonNull(source.get());
        String id = Objects.requireNonNull(initial.id);
        ResourceLocation.parse(id);
        Registration registration = new Registration(engine, source, id);
        if (SOURCES.putIfAbsent(id, registration) != null) throw new IllegalArgumentException("Duplicate Java scene " + id);
        registration.document = SceneCodec.copy(initial);
        registration.document.runtimeSource = id;
        return registration;
    }

    /** Publish the complete workspace view, so removing a local override restores compiled defaults. */
    public static void setOverrides(List<DslScene> scenes) {
        Map<String, DslScene> next = new LinkedHashMap<>();
        for (DslScene scene : scenes) if (scene.runtimeSource != null) {
            if (next.putIfAbsent(scene.runtimeSource, SceneCodec.snapshot(scene)) != null)
                throw new IllegalArgumentException("Duplicate Java scene override " + scene.runtimeSource);
        }
        overrides = Map.copyOf(next);
    }
    public static List<DslScene> overrides() { return overrides.values().stream().map(SceneCodec::snapshot).toList(); }

    @Nullable public static RuntimeEngine owner(DslScene scene) {
        Registration source = SOURCES.get(scene.runtimeSource);
        return source == null ? null : source.engine;
    }
    public static void validate(DslScene scene) {
        RuntimeEngine engine = owner(scene);
        if (engine == null) throw new IllegalArgumentException("Java host is not available: " + scene.runtimeSource);
        if (!scene.runtimeSource.equals(scene.id)) throw new IllegalArgumentException("Java scene identity cannot change: " + scene.runtimeSource);
        SceneRequirements.require(scene, engine.steps());
    }
    @Nullable public static SceneCatalog.SceneMatch findBySceneId(ResourceLocation id, int occurrence) {
        for (Registration source : SOURCES.values()) {
            var match = source.engine.catalog().findBySceneId(id, occurrence);
            if (match != null) return match;
        }
        return null;
    }
    public static List<DslScene> scenes() {
        return SOURCES.values().stream().map(source -> SceneCodec.snapshot(source.document)).toList();
    }
    public static void release(RuntimeEngine engine) {
        SOURCES.values().removeIf(source -> source.engine == engine);
        CUSTOMIZERS.remove(engine);
    }

    public static final class Registration {
        private final RuntimeEngine engine;
        private final Supplier<DslScene> source;
        private final String id;
        private DslScene document;
        private Registration(RuntimeEngine engine, Supplier<DslScene> source, String id) {
            this.engine = Objects.requireNonNull(engine);
            this.source = source;
            this.id = id;
        }
        public DslScene reload() { return reload(Objects.requireNonNull(source.get())); }
        private DslScene reload(DslScene compiled) {
            if (!id.equals(compiled.id)) throw new IllegalArgumentException("Java scene identity changed: " + id);
            compiled = SceneCodec.copy(compiled);
            for (var customizer : CUSTOMIZERS.getOrDefault(engine, List.of())) customizer.accept(compiled);
            DslScene next = SceneCodec.copy(overrides.getOrDefault(id, compiled));
            if (!id.equals(next.id)) throw new IllegalArgumentException("Java scene override identity changed: " + id);
            next.runtimeSource = id;
            next.pack = null;
            validate(next);
            List<SceneSnapshot> catalog = new ArrayList<>();
            for (DslScene scene : engine.catalog().getScenes()) if (!id.equals(scene.runtimeSource)) catalog.add(SceneSnapshot.of(scene));
            catalog.add(SceneSnapshot.of(next));
            engine.replaceCatalog(() -> catalog);
            document = next;
            return SceneCodec.snapshot(next);
        }
    }
}
