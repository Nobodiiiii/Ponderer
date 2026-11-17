package com.nododiiiii.ponderer.runtime.api;

import com.nododiiiii.ponderer.scene.DslScene;
import net.minecraft.resources.ResourceLocation;
import javax.annotation.Nullable;
import java.util.*;

/** Client-owned scene variants. Catalogs retain drafts; only the selected variant is registered. */
public final class SceneVariants {
    private static final Map<SceneCatalog, RuntimeEngine> ENGINES = new LinkedHashMap<>();
    private static final Map<String, String> SELECTED = new HashMap<>();
    private static final Map<String, RuntimeEngine> STANDALONE = new HashMap<>();
    private SceneVariants() {}

    public static void attach(RuntimeEngine engine) { ENGINES.put(engine.catalog(), engine); }
    public static void release(RuntimeEngine engine) { ENGINES.remove(engine.catalog()); }

    /** Optional bridge called reflectively by dependency-free Java exports when Lib is installed. */
    public static boolean standaloneActive(String namespace, String json) {
        if (!RuntimeFeatures.enabled(RuntimeFeature.CORE)) return true;
        DslScene scene = com.nododiiiii.ponderer.scene.SceneCodec.decode(json);
        String key = namespace + "|" + scene.identity();
        RuntimeEngine engine = STANDALONE.get(key);
        if (engine == null) {
            engine = RuntimeEngine.create(namespace, new com.nododiiiii.ponderer.runtime.spi.AssetStructureSource(namespace),
                com.nododiiiii.ponderer.runtime.spi.IngredientResolver.NONE,
                com.nododiiiii.ponderer.runtime.spi.RegistrationContribution.NONE, () -> false);
            engine.catalog().setScenes(List.of(scene));
            for (int i = 0; i < scene.scenes.size(); i++) {
                var segment = scene.scenes.get(i);
                if (segment.steps == null || segment.steps.isEmpty()) continue;
                String suffix = segment.id == null || segment.id.isBlank() ? Integer.toString(i + 1) : segment.id;
                String id = scene.id + (scene.scenes.size() > 1 ? "_" + suffix : "");
                engine.catalog().registerPonderIdMapping(id, scene.sceneKey(), i);
            }
            STANDALONE.put(key, engine);
        }
        return isActive(engine.catalog(), scene);
    }

    private record Variant(SceneCatalog catalog, DslScene scene) {
        String key() { return scene.sceneKey() + "|" + scene.runtimeSource + "|" + scene.exported; }
    }
    private static List<Variant> variants(DslScene scene) {
        List<Variant> result = new ArrayList<>();
        for (SceneCatalog catalog : ENGINES.keySet()) for (DslScene candidate : catalog.documents())
            if (Objects.equals(scene.identity(), candidate.identity())) result.add(new Variant(catalog, candidate));
        return result;
    }
    @Nullable private static Variant selected(DslScene scene) {
        var variants = variants(scene);
        String preferred = SELECTED.get(scene.identity());
        for (Variant variant : variants) if (variant.key().equals(preferred)) return variant;
        for (Variant variant : variants) if (variant.scene.exported) return variant;
        return variants.isEmpty() ? null : variants.getFirst();
    }
    public static boolean isActive(SceneCatalog catalog, DslScene scene) {
        Variant selected = selected(scene);
        return selected == null || selected.catalog == catalog && Objects.equals(selected.scene.sceneKey(), scene.sceneKey());
    }
    public static boolean hasAlternative(DslScene scene) { return variants(scene).size() > 1; }
    public static boolean isStandalone(DslScene scene) {
        RuntimeEngine engine = owner(scene);
        return engine != null && STANDALONE.containsValue(engine);
    }
    @Nullable public static String preference(DslScene scene) { return SELECTED.get(scene.identity()); }
    public static void restorePreference(DslScene scene, @Nullable String preference) {
        if (preference == null) SELECTED.remove(scene.identity());
        else SELECTED.put(scene.identity(), preference);
    }
    /** Cycles all installed versions; the host reloads Ponder after changing the selection. */
    public static DslScene selectNext(DslScene scene) {
        var variants = variants(scene);
        Variant current = selected(scene);
        if (variants.size() < 2 || current == null) return scene;
        int currentIndex = 0;
        for (int i = 0; i < variants.size(); i++)
            if (variants.get(i).catalog == current.catalog && variants.get(i).key().equals(current.key())) currentIndex = i;
        Variant next = variants.get((currentIndex + 1) % variants.size());
        SELECTED.put(scene.identity(), next.key());
        return com.nododiiiii.ponderer.scene.SceneCodec.snapshot(next.scene);
    }
    @Nullable public static RuntimeEngine owner(DslScene scene) {
        for (Variant variant : variants(scene))
            if (Objects.equals(variant.scene.sceneKey(), scene.sceneKey()) && variant.scene.exported == scene.exported
                && Objects.equals(variant.scene.runtimeSource, scene.runtimeSource)) return ENGINES.get(variant.catalog);
        return null;
    }
    public static List<DslScene> activeScenes() {
        return ENGINES.keySet().stream().flatMap(catalog -> catalog.getActiveScenes().stream()).toList();
    }
    @Nullable public static SceneCatalog.SceneMatch findBySceneId(ResourceLocation id, int occurrence) {
        for (SceneCatalog catalog : ENGINES.keySet()) {
            var match = catalog.findBySceneId(id, occurrence);
            if (match != null) return match;
        }
        return null;
    }
    /** Categories use their pre-export ID so all relocated copies share one registration. */
    public static ResourceLocation tagId(DslScene scene, String id) {
        ResourceLocation parsed = ResourceLocation.parse(id);
        if (PonderAttribution.TAG_PATH.equals(parsed.getPath())) return PonderAttribution.tag("ponderer");
        var definition = scene.customTags == null ? null : scene.customTags.get(id);
        return definition == null || definition.internalId == null ? parsed : ResourceLocation.parse(definition.internalId);
    }
}
