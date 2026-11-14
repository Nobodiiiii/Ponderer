package com.nododiiiii.ponderer.scene;

import java.util.List;
import java.util.Set;

/** Resolves legacy pool indices and named structure references without doing I/O. */
public final class StructureReferences {
    private StructureReferences() {}

    public static void collect(@javax.annotation.Nullable DslScene scene, Set<String> structures) {
        if (scene == null || structures == null) {
            return;
        }

        List<String> pool = getStructurePool(scene);
        for (String ref : pool) {
            addStructureReference(ref, structures);
        }

        if (scene.scenes == null) {
            return;
        }

        for (DslScene.SceneSegment segment : scene.scenes) {
            if (segment == null || segment.steps == null) {
                continue;
            }
            for (DslScene.DslStep step : segment.steps) {
                if (step == null || step.structure == null || step.structure.isBlank()) {
                    continue;
                }
                addStructureReference(resolveStructureReference(pool, step.structure), structures);
            }
        }
    }

    private static List<String> getStructurePool(DslScene scene) {
        if (scene.structures != null && !scene.structures.isEmpty()) {
            return scene.structures;
        }
        if (scene.structure != null && !scene.structure.isBlank()) {
            return List.of(scene.structure);
        }
        return List.of();
    }

    @javax.annotation.Nullable
    private static String resolveStructureReference(List<String> pool, String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        String trimmed = ref.trim();
        Integer parsed = tryParseInt(trimmed);
        if (parsed == null) {
            return trimmed;
        }

        int index = -1;
        if (parsed >= 1 && parsed <= pool.size()) {
            index = parsed - 1;
        } else if (parsed >= 0 && parsed < pool.size()) {
            index = parsed;
        }
        return index >= 0 ? pool.get(index) : null;
    }

    private static void addStructureReference(@javax.annotation.Nullable String ref, Set<String> structures) {
        if (ref == null || ref.isBlank()) {
            return;
        }
        String trimmed = ref.trim();
        if (!trimmed.matches("\\d+")) {
            structures.add(trimmed);
        }
    }

    @javax.annotation.Nullable
    private static Integer tryParseInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

}
