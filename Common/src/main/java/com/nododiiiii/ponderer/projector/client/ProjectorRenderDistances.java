package com.nododiiiii.ponderer.projector.client;

public final class ProjectorRenderDistances {

    public static final double OVERLAY_RENDER_DISTANCE = 32.0D;
    public static final double PROJECTION_RENDER_DISTANCE = 64.0D;
    public static final double CLIENT_PRELOAD_PADDING = 8.0D;
    public static final double OVERLAY_RENDER_DISTANCE_SQR =
        OVERLAY_RENDER_DISTANCE * OVERLAY_RENDER_DISTANCE;
    public static final double PROJECTION_RENDER_DISTANCE_SQR =
        PROJECTION_RENDER_DISTANCE * PROJECTION_RENDER_DISTANCE;
    public static final double CLIENT_PRELOAD_DISTANCE_SQR =
        (PROJECTION_RENDER_DISTANCE + CLIENT_PRELOAD_PADDING)
            * (PROJECTION_RENDER_DISTANCE + CLIENT_PRELOAD_PADDING);

    private ProjectorRenderDistances() {
    }
}
