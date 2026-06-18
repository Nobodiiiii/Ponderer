package com.nododiiiii.ponderer.projector.client;

import java.util.ArrayList;
import java.util.List;

public final class ProjectorWorldOverlayQueue {

    private static final List<QueuedScene> QUEUED_SCENES = new ArrayList<>();
    private static final List<QueuedProjectionGlow> QUEUED_GLOWS = new ArrayList<>();
    private static final List<QueuedOverlay> QUEUED = new ArrayList<>();

    private ProjectorWorldOverlayQueue() {
    }

    public static void beginFrame() {
        QUEUED_SCENES.clear();
        QUEUED_GLOWS.clear();
        QUEUED.clear();
        ProjectorBlockEntityRenderer.beginFrame();
    }

    static void enqueueScene(ProjectorBlockEntityRenderer renderer,
                             ProjectorBlockEntityRenderer.DeferredProjectedScene scene) {
        if (scene == null) {
            return;
        }
        QUEUED_SCENES.add(new QueuedScene(renderer, scene));
    }

    static void enqueueProjectionGlow(ProjectorBlockEntityRenderer renderer,
                                      ProjectorBlockEntityRenderer.DeferredProjectionGlow glow) {
        if (glow == null) {
            return;
        }
        QUEUED_GLOWS.add(new QueuedProjectionGlow(renderer, glow));
    }

    static void enqueue(ProjectorBlockEntityRenderer renderer,
                        ProjectorBlockEntityRenderer.DeferredOverlayBatch batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        QUEUED.add(new QueuedOverlay(renderer, batch));
    }

    public static void render() {
        if (!QUEUED_SCENES.isEmpty()) {
            List<QueuedScene> queuedScenes = List.copyOf(QUEUED_SCENES);
            QUEUED_SCENES.clear();
            for (QueuedScene scene : queuedScenes) {
                scene.renderer().renderDeferredProjectedScene(scene.scene());
            }
        }

        if (!QUEUED_GLOWS.isEmpty()) {
            List<QueuedProjectionGlow> queuedGlows = List.copyOf(QUEUED_GLOWS);
            QUEUED_GLOWS.clear();
            for (QueuedProjectionGlow glow : queuedGlows) {
                glow.renderer().renderDeferredProjectionGlow(glow.glow());
            }
        }

        if (!QUEUED.isEmpty()) {
            List<QueuedOverlay> queued = List.copyOf(QUEUED);
            QUEUED.clear();
            for (QueuedOverlay overlay : queued) {
                overlay.renderer().renderDeferredOverlayBatch(overlay.batch());
            }
        }
    }

    private record QueuedScene(ProjectorBlockEntityRenderer renderer,
                               ProjectorBlockEntityRenderer.DeferredProjectedScene scene) {
    }

    private record QueuedProjectionGlow(ProjectorBlockEntityRenderer renderer,
                                        ProjectorBlockEntityRenderer.DeferredProjectionGlow glow) {
    }

    private record QueuedOverlay(ProjectorBlockEntityRenderer renderer,
                                 ProjectorBlockEntityRenderer.DeferredOverlayBatch batch) {
    }
}
