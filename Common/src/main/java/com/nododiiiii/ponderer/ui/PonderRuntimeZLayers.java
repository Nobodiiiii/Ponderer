package com.nododiiiii.ponderer.ui;

/**
 * Runtime z layers for PonderUI overlays.
 *
 * After {@code PonderUIMixin#ponderer$liftSceneOutOfBackgroundDepth} pushes the scene's
 * pose-z to +5000 (to escape the depth-fight precision band around NDC ~0.76), every
 * subsequent UI element that draws with depth-test enabled must sit *above* the scene's
 * pose-z plus the scene's own geometric depth extent (≈ ±300 in pose units after the
 * scene transform scales blocks to pixels). All layers below are biased by +6000 to
 * clear that envelope with a safety margin and preserve their previous relative order.
 */
public final class PonderRuntimeZLayers {
    private static final int SCENE_LIFT_BIAS = 6000;

    public static final int SCENE_UI_BASE = 400;

    public static final int PONDER_BACKGROUND_LAYER = SCENE_LIFT_BIAS;
    public static final int SLOT_ONLY_LAYER = SCENE_LIFT_BIAS + 440;
    public static final int JEI_OVERLAY_LAYER = SCENE_LIFT_BIAS + 480;
    public static final int PONDER_TEXT_BASELINE_LAYER = SCENE_LIFT_BIAS + 500;
    public static final int TOOLTIP_LAYER = SCENE_LIFT_BIAS + 1000;
    public static final int PONDER_BUTTON_LAYER = SCENE_LIFT_BIAS + 1200;

    private PonderRuntimeZLayers() {
    }
}
