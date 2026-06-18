package com.nododiiiii.ponderer.projector.client;

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
}
