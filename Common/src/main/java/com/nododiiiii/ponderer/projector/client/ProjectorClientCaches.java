package com.nododiiiii.ponderer.projector.client;

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



    /** Tags are populated after the initial Ponder registration and may change on /reload. */

}
