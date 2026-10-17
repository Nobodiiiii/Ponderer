package com.nododiiiii.ponderer.projector.client;

import com.nododiiiii.ponderer.ponder.SceneItemBindings;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.core.BlockPos;

public final class ProjectorClientCaches {

    private ProjectorClientCaches() {
    }

    public static void invalidateAll() {
        ProjectorPlaybackState.clearAll();
        ProjectorRenderBounds.clearCache();
    }

    public static void invalidate(BlockPos pos) {
        ProjectorPlaybackState.clear(pos);
        ProjectorRenderBounds.clear(pos);
    }

    public static void reloadPonderIndexAndInvalidate() {
        try {
            PonderIndex.reload();
        } finally {
            invalidateAll();
        }
    }

    /** Tags are populated after the initial Ponder registration and may change on /reload. */
    public static void onItemTagsUpdated() {
        if (SceneRuntime.getScenes().stream()
                .anyMatch(SceneItemBindings::hasTagBindings)) {
            reloadPonderIndexAndInvalidate();
        }
    }
}
