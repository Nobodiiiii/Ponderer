package com.nododiiiii.ponderer.projector.client;

import net.createmod.ponder.foundation.PonderIndex;

public final class ProjectorClientCaches {

    private ProjectorClientCaches() {
    }

    public static void invalidateAll() {
        ProjectorPlaybackState.clearAll();
        ProjectorRenderBounds.clearCache();
    }

    public static void reloadPonderIndexAndInvalidate() {
        try {
            PonderIndex.reload();
        } finally {
            invalidateAll();
        }
    }
}
