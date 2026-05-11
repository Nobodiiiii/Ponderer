package com.nododiiiii.ponderer.ponder;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

public record JavaModuleExportRequest(Path targetRoot, Set<String> selectedSceneKeys) {

    public JavaModuleExportRequest {
        targetRoot = targetRoot == null ? null : targetRoot.toAbsolutePath().normalize();
        selectedSceneKeys = selectedSceneKeys == null
            ? Set.of()
            : Set.copyOf(new LinkedHashSet<>(selectedSceneKeys));
    }

    public boolean exportAllLocalScenes() {
        return selectedSceneKeys.isEmpty();
    }

    @Nullable
    public String firstSelectedSceneKey() {
        return selectedSceneKeys.stream().findFirst().orElse(null);
    }
}
