package com.nododiiiii.ponderer.ponder;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SceneRuntime {
    private static volatile List<DslScene> scenes = List.of();

    /** Pattern to strip _partN or _N suffix from scene IDs for multi-scene matching. */
    private static final Pattern SCENE_SUFFIX = Pattern.compile("^(.+?)(?:_part(\\d+)|_(\\d+))$");

    /**
     * Mapping from PonderScene full ID (e.g. "ponderer:example") to the list of DslScene sceneKeys
     * that were registered under that ID, in registration order.
     * Populated by DynamicPonderPlugin.registerScene().
     */
    private static final Map<String, List<SceneKeyEntry>> ponderIdToSceneKeys = new ConcurrentHashMap<>();

    /**
     * Entry recording a DslScene's sceneKey and the scene segment index for a registered PonderScene ID.
     */
    public record SceneKeyEntry(String sceneKey, int sceneIndex) {}

    private SceneRuntime() {
    }

    public static List<DslScene> getScenes() {
        return scenes;
    }

    public static void setScenes(List<DslScene> newScenes) {
        scenes = Collections.unmodifiableList(new ArrayList<>(newScenes));
    }

    /**
     * Clear the ponder ID → scene key mapping. Called before scene registration.
     */
    public static void clearPonderIdMapping() {
        ponderIdToSceneKeys.clear();
    }

    /**
     * Register a mapping from a PonderScene's full ID to the DslScene's sceneKey and segment index.
     * Called by DynamicPonderPlugin during scene registration.
     *
     * @param ponderSceneId the full PonderScene ID (e.g. "ponderer:example" or "ponderer:example_1")
     * @param sceneKey the DslScene's sceneKey (e.g. "ponderer:example" or "[MyPack] ponderer:example")
     * @param sceneIndex the scene segment index within the DslScene
     */
    public static void registerPonderIdMapping(String ponderSceneId, String sceneKey, int sceneIndex) {
        ponderIdToSceneKeys.computeIfAbsent(ponderSceneId, k -> new ArrayList<>())
                .add(new SceneKeyEntry(sceneKey, sceneIndex));
    }

    /**
     * Resolve the set of PonderScene IDs that belong to the given scene keys.
     * Used to filter PonderUI's scene list to only show scenes from a specific
     * pack/NBT group.
     *
     * @param sceneKeys the DslScene sceneKeys (e.g. "[MyPack] ponderer:example")
     * @return set of PonderScene full IDs (e.g. "ponderer:example") that match,
     *         paired with the occurrence index for each
     */
    public static Set<PonderSceneRef> resolvePonderSceneRefs(Collection<String> sceneKeys) {
        Set<String> keySet = sceneKeys instanceof Set ? (Set<String>) sceneKeys : new HashSet<>(sceneKeys);
        Set<PonderSceneRef> result = new HashSet<>();
        for (var entry : ponderIdToSceneKeys.entrySet()) {
            String ponderSceneId = entry.getKey();
            List<SceneKeyEntry> entries = entry.getValue();
            int occurrence = 0;
            for (SceneKeyEntry ske : entries) {
                if (keySet.contains(ske.sceneKey())) {
                    result.add(new PonderSceneRef(ponderSceneId, occurrence));
                }
                occurrence++;
            }
        }
        return result;
    }

    /**
     * Reference to a specific PonderScene in the UI's scene list.
     * @param ponderSceneId the PonderScene's full ID string
     * @param occurrenceIndex 0-based index among scenes with the same ID
     */
    public record PonderSceneRef(String ponderSceneId, int occurrenceIndex) {}

    /**
     * Find a scene by its unique scene key.
     * Key format: "ponderer:example" (local) or "[my_pack] ponderer:example" (pack).
     */
    @Nullable
    public static DslScene findByKey(String sceneKey) {
        if (sceneKey == null) return null;
        for (DslScene scene : scenes) {
            if (scene.id == null) continue;
            if (sceneKey.equals(scene.sceneKey())) return scene;
        }
        return null;
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
        return findBySceneId(ponderSceneId, -1);
    }

    /**
     * Find the DslScene and scene index corresponding to a PonderScene's ResourceLocation ID,
     * using the occurrence index to disambiguate when multiple DslScenes share the same
     * PonderScene ID (e.g. scenes from different packs).
     *
     * @param ponderSceneId the ResourceLocation from PonderScene.getId()
     * @param occurrenceIndex 0-based index among PonderScenes with the same ID (from PonderUI's scene list),
     *                        or -1 to use legacy first-match behavior
     * @return the matching DslScene and scene index, or null if not found
     */
    @Nullable
    public static SceneMatch findBySceneId(ResourceLocation ponderSceneId, int occurrenceIndex) {
        if (ponderSceneId == null) return null;

        String fullId = ponderSceneId.toString();

        // Try the registration-time mapping first (most reliable)
        List<SceneKeyEntry> entries = ponderIdToSceneKeys.get(fullId);
        if (entries != null && !entries.isEmpty()) {
            SceneKeyEntry entry;
            if (occurrenceIndex >= 0 && occurrenceIndex < entries.size()) {
                entry = entries.get(occurrenceIndex);
            } else {
                entry = entries.get(0);
            }
            DslScene found = findByKey(entry.sceneKey());
            if (found != null) {
                return new SceneMatch(found, entry.sceneIndex());
            }
        }

        // Fallback to legacy matching for backward compatibility
        return findBySceneIdLegacy(ponderSceneId);
    }

    /**
     * Legacy matching logic: iterate scenes list and return first match.
     */
    @Nullable
    private static SceneMatch findBySceneIdLegacy(ResourceLocation ponderSceneId) {
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
