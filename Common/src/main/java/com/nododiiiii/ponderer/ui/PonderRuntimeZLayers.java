package com.nododiiiii.ponderer.ui;

/**
 * Runtime z layers for show_interface inside PonderUI.
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
    public static final int MEKANISM_EMBEDDED_RENDER_BIAS = 400;
    public static final int EMBEDDED_GUI_BACKGROUND_LAYER = SCENE_LIFT_BIAS + 120;
    public static final int EMBEDDED_GUI_WIDGET_LAYER = SCENE_LIFT_BIAS + 160;
    public static final int EMBEDDED_GUI_OVERLAY_LAYER = SCENE_LIFT_BIAS + 170;
    public static final int EMBEDDED_GUI_ITEM_LAYER = SCENE_LIFT_BIAS + 320;
    public static final int EMBEDDED_GUI_ITEM_DECORATION_LAYER = SCENE_LIFT_BIAS + 370;
    public static final int EMBEDDED_GUI_TOOLTIP_LAYER = SCENE_LIFT_BIAS + 390;
    public static final int SLOT_ONLY_LAYER = SCENE_LIFT_BIAS + 440;
    public static final int JEI_OVERLAY_LAYER = SCENE_LIFT_BIAS + 480;
    public static final int PONDER_TEXT_BASELINE_LAYER = SCENE_LIFT_BIAS + 500;
    public static final int TOOLTIP_LAYER = SCENE_LIFT_BIAS + 1000;
    public static final int PONDER_BUTTON_LAYER = SCENE_LIFT_BIAS + 1200;

    private static final int VANILLA_SLOT_BLIT_OFFSET = 100;
    private static final int VANILLA_ITEM_BLIT_OFFSET = 150;
    private static final int VANILLA_FLOATING_ITEM_BLIT_OFFSET = 232;
    private static final int VANILLA_TOOLTIP_BLIT_OFFSET = 400;

    private PonderRuntimeZLayers() {
    }

    public static int embeddedSlotPoseZ() {
        return EMBEDDED_GUI_OVERLAY_LAYER - VANILLA_SLOT_BLIT_OFFSET;
    }

    public static int embeddedFloatingItemPoseZ() {
        return EMBEDDED_GUI_ITEM_LAYER - VANILLA_FLOATING_ITEM_BLIT_OFFSET - VANILLA_ITEM_BLIT_OFFSET;
    }

    public static int embeddedTooltipPoseZ() {
        return EMBEDDED_GUI_TOOLTIP_LAYER - VANILLA_TOOLTIP_BLIT_OFFSET;
    }
}
