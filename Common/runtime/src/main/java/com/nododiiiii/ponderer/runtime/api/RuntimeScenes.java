package com.nododiiiii.ponderer.runtime.api;

import com.nododiiiii.ponderer.runtime.core.SceneChunkSource;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Opts native scenes into Lib chunk data, entity ticking and cache reclamation without retaining their worlds.
 * Text continues to use upstream Ponder localization; already resolved Lib text opts in through {@link SceneTexts}.
 */
public final class RuntimeScenes {
    private static final Set<PonderLevel> WORLDS = Collections.newSetFromMap(new WeakHashMap<>());
    private RuntimeScenes() {}
    public static void adopt(PonderScene scene) {
        RuntimeFeatures.require(RuntimeFeature.CORE, "RuntimeScenes.adopt");
        PonderLevel world = scene.getWorld();
        if (world != null && WORLDS.add(world)) SceneChunkSource.install(world);
    }
    public static boolean owns(PonderLevel world) { return WORLDS.contains(world); }
}
