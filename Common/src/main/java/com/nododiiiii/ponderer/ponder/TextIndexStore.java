package com.nododiiiii.ponderer.ponder;

import net.createmod.ponder.foundation.PonderScene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Per-scene index of text / shared_text steps and the scene tick at which each
 * one becomes visible. Populated by {@link TextMarkerInstruction} during scene
 * begin(), cleared by the PonderScene mixin at the head of begin() so the list
 * does not grow on replay.
 */
public final class TextIndexStore {

    public record Entry(int tick, String preview, boolean shared) {}

    private static final WeakHashMap<PonderScene, List<Entry>> MAP = new WeakHashMap<>();

    private TextIndexStore() {}

    public static synchronized void clear(PonderScene scene) {
        MAP.remove(scene);
    }

    public static synchronized void append(PonderScene scene, int tick, String preview, boolean shared) {
        MAP.computeIfAbsent(scene, s -> new ArrayList<>())
            .add(new Entry(tick, preview == null ? "" : preview, shared));
    }

    public static synchronized List<Entry> get(PonderScene scene) {
        List<Entry> list = MAP.get(scene);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(list));
    }
}
