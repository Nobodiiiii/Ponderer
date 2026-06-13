package com.nododiiiii.ponderer.projector.client;

import java.util.ArrayList;
import java.util.List;

public final class ProjectorWorldOverlayQueue {

    private static final List<QueuedOverlay> QUEUED = new ArrayList<>();

    private ProjectorWorldOverlayQueue() {
    }

    public static void beginFrame() {
        QUEUED.clear();
    }

    static void enqueue(ProjectorBlockEntityRenderer renderer,
                        ProjectorBlockEntityRenderer.DeferredOverlayBatch batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        QUEUED.add(new QueuedOverlay(renderer, batch));
    }

    public static void render() {
        if (QUEUED.isEmpty()) {
            return;
        }

        List<QueuedOverlay> queued = List.copyOf(QUEUED);
        QUEUED.clear();
        for (QueuedOverlay overlay : queued) {
            overlay.renderer().renderDeferredOverlayBatch(overlay.batch());
        }
    }

    private record QueuedOverlay(ProjectorBlockEntityRenderer renderer,
                                 ProjectorBlockEntityRenderer.DeferredOverlayBatch batch) {
    }
}
