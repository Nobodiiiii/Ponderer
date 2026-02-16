package com.nododiiiii.ponderer.ponder;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SceneRuntime {
    private static volatile List<DslScene> scenes = List.of();

    /** Pattern to strip _partN or _N suffix from scene IDs for multi-scene matching. */
    private static final Pattern SCENE_SUFFIX = Pattern.compile("^(.+?)(?:_part(\\d+)|_(\\d+))$");

    private SceneRuntime() {
    }

    public static List<DslScene> getScenes() {
        return scenes;
    }

    public static void setScenes(List<DslScene> newScenes) {
        scenes = Collections.unmodifiableList(new ArrayList<>(newScenes));
    }

    /**
     * Result record for scene lookup.
     */
    public record SceneMatch(DslScene scene, int sceneIndex) {}

    /**
     * Find the DslScene and scene index corresponding to a PonderScene's ResourceLocation ID.
     *
     * For single-scene ponders: PonderScene.getId() == DslScene.id  (exact match)
     * For multi-scene ponders: PonderScene.getId() == DslScene.id + "_partN" or "_N"
     *
     * @param ponderSceneId the ResourceLocation from PonderScene.getId()
     * @return the matching DslScene and scene index, or null if not found
     */
    @Nullable
    public static SceneMatch findBySceneId(ResourceLocation ponderSceneId) {
        if (ponderSceneId == null) return null;

        String fullId = ponderSceneId.toString();
        String path = ponderSceneId.getPath();

        // First: try exact match
        for (DslScene scene : scenes) {
            if (scene.id == null) continue;
            ResourceLocation sceneId = ResourceLocation.tryParse(scene.id);
            if (sceneId == null) continue;

            if (sceneId.equals(ponderSceneId)) {
                return new SceneMatch(scene, 0);
            }
        }

        // Second: try stripping scene suffix (_partN or _N)
        Matcher matcher = SCENE_SUFFIX.matcher(path);
        if (matcher.matches()) {
            String basePath = matcher.group(1);
            String partNum = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            int sceneIndex = 0;
            try {
                sceneIndex = Integer.parseInt(partNum) - 1; // 1-based to 0-based
            } catch (NumberFormatException ignored) {}

            ResourceLocation baseId = new ResourceLocation(ponderSceneId.getNamespace(), basePath);
            for (DslScene scene : scenes) {
                if (scene.id == null) continue;
                ResourceLocation sceneId = ResourceLocation.tryParse(scene.id);
                if (sceneId == null) continue;

                if (sceneId.equals(baseId)) {
                    return new SceneMatch(scene, Math.max(0, sceneIndex));
                }
            }
        }

        // Third: try prefix match (DslScene.id path is a prefix of ponderSceneId path)
            // Also check named scene IDs (e.g. scene id "example" + scene id "structure" -> "example_structure")
        for (DslScene scene : scenes) {
            if (scene.id == null) continue;
            ResourceLocation sceneId = ResourceLocation.tryParse(scene.id);
            if (sceneId == null) continue;

            if (!ponderSceneId.getNamespace().equals(sceneId.getNamespace())) continue;
            if (!path.startsWith(sceneId.getPath())) continue;

            String remainder = path.substring(sceneId.getPath().length());
            if (remainder.isEmpty()) continue; // exact match was already handled
            if (!remainder.startsWith("_")) continue;
            String suffix = remainder.substring(1); // strip leading "_"

            // Try numeric index first (e.g. "_1", "_2")
            try {
                int idx = Integer.parseInt(suffix) - 1;
                return new SceneMatch(scene, Math.max(0, idx));
            } catch (NumberFormatException ignored) {}

            // Try matching against named scene IDs (e.g. "_structure", "_entity_text")
            if (scene.scenes != null) {
                for (int i = 0; i < scene.scenes.size(); i++) {
                    DslScene.SceneSegment sc = scene.scenes.get(i);
                    if (sc.id != null && suffix.equals(sc.id)) {
                        return new SceneMatch(scene, i);
                    }
                }
                // Also try suffix matching for scene ids containing underscores
                // e.g. path "example_entity_text" -> suffix "entity_text" matches scene id "entity_text"
                for (int i = 0; i < scene.scenes.size(); i++) {
                    DslScene.SceneSegment sc = scene.scenes.get(i);
                    if (sc.id != null && suffix.endsWith(sc.id)) {
                        return new SceneMatch(scene, i);
                    }
                }
            }

            // Fallback: scene 0
            return new SceneMatch(scene, 0);
        }

        return null;
    }
}