package com.nododiiiii.ponderer.runtime.api;

import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Opts native scenes into Lib entity ticking and cache reclamation without retaining their worlds. */
public final class RuntimeScenes {
    private static final Set<PonderLevel> WORLDS = Collections.newSetFromMap(new WeakHashMap<>());
    private RuntimeScenes() {}
    public static void adopt(PonderScene scene) {
        RuntimeFeatures.require(RuntimeFeature.CORE, "RuntimeScenes.adopt");
        WORLDS.add(scene.getWorld());
    }
    public static boolean owns(PonderLevel world) { return WORLDS.contains(world); }
}
