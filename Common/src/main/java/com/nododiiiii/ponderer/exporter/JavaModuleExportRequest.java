package com.nododiiiii.ponderer.exporter;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

public record JavaModuleExportRequest(Path targetRoot, Set<String> selectedSceneKeys,
                                      java.util.List<com.nododiiiii.ponderer.scene.SceneSnapshot> scenes, Mode mode,
                                      LegacyAction legacyAction) {
    public enum Mode { JSON_LIB, JAVA_LIB, JAVA_SUPPORT }
    public static final Mode DEFAULT_MODE = Mode.JAVA_LIB;
    public enum LegacyAction { PROMPT, CONVERT, DISCARD }

    public JavaModuleExportRequest {
        mode = mode == null ? DEFAULT_MODE : mode;
        legacyAction = legacyAction == null ? LegacyAction.PROMPT : legacyAction;
        scenes = scenes == null ? java.util.List.of() : java.util.List.copyOf(scenes);
        targetRoot = targetRoot == null ? null : targetRoot.toAbsolutePath().normalize();
        selectedSceneKeys = selectedSceneKeys == null
            ? Set.of()
            : Set.copyOf(new LinkedHashSet<>(selectedSceneKeys));
    }

    public JavaModuleExportRequest(Path root, Set<String> keys,
                                   java.util.List<com.nododiiiii.ponderer.scene.SceneSnapshot> scenes, Mode mode) {
        this(root, keys, scenes, mode, LegacyAction.PROMPT);
    }

    public JavaModuleExportRequest withLegacyAction(LegacyAction action) {
        return new JavaModuleExportRequest(targetRoot, selectedSceneKeys, scenes, mode, action);
    }

    public JavaModuleExportRequest(Path root, Set<String> keys, java.util.List<com.nododiiiii.ponderer.scene.SceneSnapshot> scenes) { this(root, keys, scenes, DEFAULT_MODE); }
    public JavaModuleExportRequest(Path root, Set<String> keys) { this(root, keys, java.util.List.of()); }

    @Nullable
    public String firstSelectedSceneKey() {
        return selectedSceneKeys.stream().findFirst().orElse(null);
    }
}
