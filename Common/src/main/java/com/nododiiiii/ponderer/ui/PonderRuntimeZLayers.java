package com.nododiiiii.ponderer.ui;

/**
 * Runtime z layers for show_interface inside PonderUI.
 * Values here are local offsets after Ponder's global +400 projection shift.
 */
public final class PonderRuntimeZLayers {
    public static final int SCENE_UI_BASE = 400;

    public static final int PONDER_BACKGROUND_LAYER = 0;
    public static final int MEKANISM_EMBEDDED_RENDER_BIAS = 400;
    public static final int EMBEDDED_GUI_BACKGROUND_LAYER = 120;
    public static final int EMBEDDED_GUI_WIDGET_LAYER = 160;
    public static final int EMBEDDED_GUI_OVERLAY_LAYER = 170;
    public static final int EMBEDDED_GUI_ITEM_LAYER = 320;
    public static final int EMBEDDED_GUI_ITEM_DECORATION_LAYER = 370;
    public static final int EMBEDDED_GUI_TOOLTIP_LAYER = 390;
    public static final int SLOT_ONLY_LAYER = 440;
    public static final int JEI_OVERLAY_LAYER = 480;
    public static final int PONDER_TEXT_BASELINE_LAYER = 500;
    public static final int TOOLTIP_LAYER = 1000;
    public static final int PONDER_BUTTON_LAYER = 1200;

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
