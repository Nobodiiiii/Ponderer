package com.nododiiiii.ponderer.projector.client;

import net.createmod.ponder.foundation.PonderScene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

public final class ProjectorCueIndexStore {

    private static final WeakHashMap<PonderScene, List<ProjectorSceneBundle.OverlayCue>> CUES = new WeakHashMap<>();

    private ProjectorCueIndexStore() {
    }

    public static synchronized void clear(PonderScene scene) {
        CUES.remove(scene);
    }

    public static synchronized void append(PonderScene scene, ProjectorSceneBundle.OverlayCue cue) {
        if (cue == null) {
            return;
        }
        CUES.computeIfAbsent(scene, ignored -> new ArrayList<>()).add(cue);
    }

    public static synchronized List<ProjectorSceneBundle.OverlayCue> get(PonderScene scene) {
        List<ProjectorSceneBundle.OverlayCue> cues = CUES.get(scene);
        if (cues == null || cues.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(cues));
    }
}
