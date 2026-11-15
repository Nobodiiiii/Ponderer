package com.nododiiiii.ponderer.application.api;

import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;
import java.util.function.Supplier;

/** Product consumers see the runtime contract, while bootstrap alone constructs its implementation. */
public final class RuntimeAccess {
    private static Supplier<RuntimeEngine> source;
    private RuntimeAccess() {}
    public static void install(Supplier<RuntimeEngine> engine) { source = java.util.Objects.requireNonNull(engine); }
    public static RuntimeEngine engine() {
        if (source == null) throw new IllegalStateException("Runtime host has not been installed");
        return source.get();
    }
    public static RuntimeEngine engineFor(com.nododiiiii.ponderer.scene.DslScene scene) {
        var owner = com.nododiiiii.ponderer.runtime.api.EditableScenes.owner(scene);
        return owner == null ? engine() : owner;
    }
    public static java.util.List<com.nododiiiii.ponderer.scene.DslScene> scenes() {
        var scenes = new java.util.LinkedHashMap<String, com.nododiiiii.ponderer.scene.DslScene>();
        for (var scene : engine().catalog().getScenes()) scenes.put(scene.sceneKey(), scene);
        for (var scene : com.nododiiiii.ponderer.runtime.api.EditableScenes.scenes()) scenes.put(scene.sceneKey(), scene);
        return java.util.List.copyOf(scenes.values());
    }
    public static java.util.Set<com.nododiiiii.ponderer.runtime.api.SceneCatalog.PonderSceneRef> resolvePonderSceneRefs(
            java.util.Collection<String> keys) {
        var refs = new java.util.LinkedHashSet<com.nododiiiii.ponderer.runtime.api.SceneCatalog.PonderSceneRef>();
        for (var scene : scenes()) if (keys.contains(scene.sceneKey()))
            refs.addAll(engineFor(scene).catalog().resolvePonderSceneRefs(java.util.List.of(scene.sceneKey())));
        return refs;
    }
    public static com.nododiiiii.ponderer.runtime.api.SceneCatalog.SceneMatch findBySceneId(
            net.minecraft.resources.ResourceLocation id, int occurrence) {
        var match = engine().catalog().findBySceneId(id, occurrence);
        return match != null ? match : com.nododiiiii.ponderer.runtime.api.EditableScenes.findBySceneId(id, occurrence);
    }
}
