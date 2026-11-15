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
    public static com.nododiiiii.ponderer.runtime.api.SceneCatalog.SceneMatch findBySceneId(
            net.minecraft.resources.ResourceLocation id, int occurrence) {
        var match = engine().catalog().findBySceneId(id, occurrence);
        return match != null ? match : com.nododiiiii.ponderer.runtime.api.EditableScenes.findBySceneId(id, occurrence);
    }
}
