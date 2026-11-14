package com.nododiiiii.ponderer.runtime.api;

import com.nododiiiii.ponderer.scene.DslScene;
import com.google.gson.Gson;
import net.createmod.ponder.foundation.PonderScene;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/** Overlay descriptions are owned by their scene; product consumers choose presentation. */
public final class OverlayTimeline {
    public record Cue(int startTick, DslScene.DslStep step) {}
    private static final WeakHashMap<PonderScene, List<Cue>> CUES = new WeakHashMap<>();
    private static final Gson COPY = new Gson();
    private OverlayTimeline() {}
    public static synchronized void clear(PonderScene scene) { CUES.remove(scene); }
    public static synchronized void append(PonderScene scene, int tick, DslScene.DslStep step) {
        CUES.computeIfAbsent(scene, ignored -> new ArrayList<>()).add(
            new Cue(tick, COPY.fromJson(COPY.toJson(step), DslScene.DslStep.class)));
    }
    public static synchronized List<Cue> get(PonderScene scene) {
        return List.copyOf(CUES.getOrDefault(scene, List.of()));
    }
}
