package com.nododiiiii.ponderer.ui;

/**
 * Unified UI layout constants used by all screens.
 * Single source of truth for spacing, sizing, and scroll behavior.
 * <p>
 * Replaces scattered per-screen constants like {@code WINDOW_W}, {@code ROW_H},
 * {@code MARGIN}, etc. to ensure consistent look across all editor pages.
 */
public final class UILayoutConstants {
    private UILayoutConstants() {}

    // ─── Window geometry ────────────────────────────────────────────────
    /** Default window width for step editor forms. */
    public static final int FORM_WINDOW_W = 220;
    /** Default window width for command / param screens. */
    public static final int PARAM_WINDOW_W = 240;
    /** Default window width for config / description / wider screens. */
    public static final int WIDE_WINDOW_W = 260;

    // ─── Row layout ─────────────────────────────────────────────────────
    /** Standard form row height (all editors). */
    public static final int ROW_H = 22;
    /** Y offset from window top to first form row. */
    public static final int FORM_TOP = 26;
    /** Left margin for labels. */
    public static final int LABEL_LEFT = 10;
    /** Label column width (step editors). */
    public static final int LABEL_W_STEP = 60;
    /** Label column width (command param screens). */
    public static final int LABEL_W_PARAM = 72;
    /** Field start X offset from window left (step editors). */
    public static final int FIELD_LEFT = 70;

    // ─── Fields ─────────────────────────────────────────────────────────
    /** Standard text / number field height. */
    public static final int FIELD_H = 18;
    /** Small number field height. */
    public static final int SMALL_FIELD_H = 18;
    /** Default small number field width. */
    public static final int SMALL_NUM_W = 38;
    /** Gap between XYZ number fields. */
    public static final int XYZ_GAP = 5;

    // ─── Buttons ────────────────────────────────────────────────────────
    /** Inline action button width (JEI, Pick, etc.). */
    public static final int ACTION_BTN_W = 14;
    /** Inline action button height. */
    public static final int ACTION_BTN_H = 12;
    /** Toggle button size. */
    public static final int TOGGLE_SIZE = 12;
    /** Gap between field edge and first action button. */
    public static final int FIELD_BTN_GAP = 6;
    /** Gap between multiple action buttons. */
    public static final int BTN_GAP = 4;
    /** Vertical offset for action buttons within a row. */
    public static final int BTN_Y_OFFSET = 3;

    // ─── Bottom fixed section ───────────────────────────────────────────
    /** Height of the fixed bottom section (keyframe toggle + confirm/cancel + error). */
    public static final int BOTTOM_SECTION_H = 60;
    /** Bottom confirm / cancel button width. */
    public static final int BOTTOM_BTN_W = 80;
    /** Bottom confirm / cancel button height. */
    public static final int BOTTOM_BTN_H = 20;

    // ─── Scrolling ──────────────────────────────────────────────────────
    /** Scroll speed in pixels per mouse-wheel notch. */
    public static final int SCROLL_SPEED = ROW_H;
    /** Scrollbar track width. */
    public static final int SCROLLBAR_W = 4;
    /** Minimum scrollbar thumb height. */
    public static final int SCROLLBAR_MIN_THUMB = 8;
    /** Vertical safety margin from screen edges when clamping window height. */
    public static final int SCREEN_MARGIN = 10;

    // ─── Colours ────────────────────────────────────────────────────────
    /** Default label text colour. */
    public static final int COLOR_LABEL = 0xCCCCCC;
    /** Hint / secondary text colour. */
    public static final int COLOR_HINT = 0x808080;
    /** Error text colour. */
    public static final int COLOR_ERROR = 0xFF5555;
    /** Panel background ARGB. */
    public static final int COLOR_BG = 0xDD_000000;
    /** Gradient border top colour. */
    public static final int COLOR_BORDER_TOP = 0x60_C0C0FF;
    /** Gradient border bottom colour. */
    public static final int COLOR_BORDER_BOT = 0x30_C0C0FF;
    /** Separator line colour. */
    public static final int COLOR_SEPARATOR = 0x60_FFFFFF;
    /** Scrollbar track colour. */
    public static final int COLOR_SCROLLBAR_BG = 0x30_FFFFFF;
    /** Scrollbar thumb colour. */
    public static final int COLOR_SCROLLBAR_FG = 0x80_FFFFFF;
    /** Scrollbar thumb hovered colour. */
    public static final int COLOR_SCROLLBAR_HOVER = 0xC0_FFFFFF;
}
