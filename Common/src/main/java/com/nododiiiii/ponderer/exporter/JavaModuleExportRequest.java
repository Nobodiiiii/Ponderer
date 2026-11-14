package com.nododiiiii.ponderer.exporter;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

public record JavaModuleExportRequest(Path targetRoot, Set<String> selectedSceneKeys,
                                      java.util.List<com.nododiiiii.ponderer.scene.SceneSnapshot> scenes, Mode mode) {
    public enum Mode { JSON_LIB, JAVA_LIB, JAVA_SUPPORT }

    public JavaModuleExportRequest {
        mode = mode == null ? Mode.JAVA_SUPPORT : mode;
        scenes = scenes == null ? java.util.List.of() : java.util.List.copyOf(scenes);
        targetRoot = targetRoot == null ? null : targetRoot.toAbsolutePath().normalize();
        selectedSceneKeys = selectedSceneKeys == null
            ? Set.of()
            : Set.copyOf(new LinkedHashSet<>(selectedSceneKeys));
    }

    public JavaModuleExportRequest(Path root, Set<String> keys, java.util.List<com.nododiiiii.ponderer.scene.SceneSnapshot> scenes) { this(root, keys, scenes, Mode.JAVA_SUPPORT); }
    public JavaModuleExportRequest(Path root, Set<String> keys) { this(root, keys, java.util.List.of()); }

    public boolean supportOnly() {
        return selectedSceneKeys.isEmpty();
    }

    @Nullable
    public String firstSelectedSceneKey() {
        return selectedSceneKeys.stream().findFirst().orElse(null);
    }
}
