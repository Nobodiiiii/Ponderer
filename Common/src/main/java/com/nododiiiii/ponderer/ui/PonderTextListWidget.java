package com.nododiiiii.ponderer.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nododiiiii.ponderer.ponder.TextIndexStore;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Side panel listing every text / shared_text entry that occurs in the current
 * Ponder scene segment. Clicking a row seeks the scene to the tick at which
 * that text appears, mirroring keyframe navigation in {@link PonderUI#seekToTime(int)}.
 *
 * Visibility is controlled by a toggle button set up in PonderUIMixin via the
 * {@code VISIBLE} flag.
 */
public class PonderTextListWidget extends AbstractWidget {

    /** Persistent (per-session) toggle for whether the panel is shown. */
    public static boolean VISIBLE = true;

    public static final int PANEL_WIDTH = 144;
    public static final int RIGHT_MARGIN = 14;
    public static final int TOP_Y = 70;
    public static final int BOTTOM_PAD = 60; // leave room for the bottom button row

    private static final int ROW_HEIGHT = 11;
    private static final int HEADER_HEIGHT = 24;
    private static final int PADDING_X = 7;
    private static final int CONTENT_RIGHT_PAD = 10;

    // Catnip-style blue/navy palette.
    private static final int BG_FILL          = 0xFF_000000;
    private static final int BG_OUTER_BORDER  = 0xC0_28324a;
    private static final int BG_INNER_BORDER  = 0x60_3d5070;
    private static final int HEADER_RULE      = 0x80_4a6080;
    private static final int HEADER_TEXT      = 0xFF_d6e4ff;
    private static final int SUBHEADER_TEXT   = 0xFF_8ab6d6;
    private static final int ACCENT_PLAYED    = 0xFF_8ab6d6;
    private static final int ACCENT_UNPLAYED  = 0xFF_3a4666;
    private static final int TEXT_PLAYED      = 0xFF_d8e6fa;
    private static final int TEXT_HOVER       = 0xFF_ffffff;
    private static final int TEXT_UNPLAYED    = 0xFF_707a90;
    private static final int ROW_HOVER_BG     = 0x60_3a5a8a;
    private static final int SCROLL_TRACK     = 0x40_28324a;
    private static final int SCROLL_THUMB     = 0xC0_8ab6d6;

    private final PonderUI ponderUi;
    private int scrollOffset;

    public PonderTextListWidget(PonderUI ponderUi) {
        super(ponderUi.width - PANEL_WIDTH - RIGHT_MARGIN, TOP_Y,
            PANEL_WIDTH, Math.max(60, ponderUi.height - TOP_Y - BOTTOM_PAD),
            Component.empty());
        this.ponderUi = ponderUi;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!shouldShow()) {
            return;
        }
        List<TextIndexStore.Entry> entries = currentEntries();
        if (entries.isEmpty()) {
            return;
        }

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        RenderSystem.enableBlend();

        // Outer border + inner border + fill (catnip-style dark navy box)
        graphics.fill(x - 2, y - 2, x + w + 2, y + h + 2, BG_OUTER_BORDER);
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, BG_INNER_BORDER);
        graphics.fill(x,     y,     x + w,     y + h,     BG_FILL);

        Font font = Minecraft.getInstance().font;

        // Bilingual header (two lines, Chinese above English subtitle)
        String headerCn = "文本进度";
        String headerEn = "Text Progress";
        graphics.drawString(font, headerCn, x + PADDING_X, y + 4, HEADER_TEXT, false);
        graphics.drawString(font, headerEn, x + PADDING_X, y + 14, SUBHEADER_TEXT, false);

        String countLabel = String.valueOf(entries.size());
        int countWidth = font.width(countLabel);
        graphics.drawString(font, countLabel,
            x + w - PADDING_X - countWidth, y + 4, SUBHEADER_TEXT, false);

        graphics.fill(x + 3, y + HEADER_HEIGHT, x + w - 3, y + HEADER_HEIGHT + 1, HEADER_RULE);

        int listTop = y + HEADER_HEIGHT + 4;
        int listBottom = y + h - 4;
        int visibleRows = Math.max(0, (listBottom - listTop) / ROW_HEIGHT);
        int maxScroll = Math.max(0, entries.size() - visibleRows);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        int currentSceneTime = ponderUi.getActiveScene().getCurrentTime();
        int textWidth = w - PADDING_X - CONTENT_RIGHT_PAD - 6;

        graphics.enableScissor(x, listTop, x + w, listBottom);
        for (int row = 0; row < visibleRows; row++) {
            int index = row + scrollOffset;
            if (index >= entries.size()) break;
            TextIndexStore.Entry e = entries.get(index);

            int rowY = listTop + row * ROW_HEIGHT;
            boolean played = e.tick() <= currentSceneTime;
            boolean hovered = mouseX >= x + 3 && mouseX < x + w - CONTENT_RIGHT_PAD
                && mouseY >= rowY - 1 && mouseY < rowY + ROW_HEIGHT - 1;

            if (hovered) {
                graphics.fill(x + 3, rowY - 1, x + w - CONTENT_RIGHT_PAD, rowY + ROW_HEIGHT - 1, ROW_HOVER_BG);
            }

            int accent = played ? ACCENT_PLAYED : ACCENT_UNPLAYED;
            graphics.fill(x + 3, rowY, x + 5, rowY + ROW_HEIGHT - 2, accent);

            int textColor;
            if (hovered) {
                textColor = TEXT_HOVER;
            } else if (played) {
                textColor = TEXT_PLAYED;
            } else {
                textColor = TEXT_UNPLAYED;
            }
            FormattedCharSequence line = truncate(font, e, textWidth);
            graphics.drawString(font, line, x + PADDING_X + 2, rowY + 1, textColor, false);
        }
        graphics.disableScissor();

        if (entries.size() > visibleRows && visibleRows > 0) {
            int trackTop = listTop;
            int trackBottom = listBottom;
            int trackH = trackBottom - trackTop;
            int thumbH = Math.max(10, trackH * visibleRows / entries.size());
            int thumbY = trackTop + (trackH - thumbH) * scrollOffset / Math.max(1, maxScroll);
            int barX = x + w - 5;
            graphics.fill(barX, trackTop, barX + 2, trackBottom, SCROLL_TRACK);
            graphics.fill(barX, thumbY, barX + 2, thumbY + thumbH, SCROLL_THUMB);
        }

        TextIndexStore.Entry hoveredEntry = entryAt(mouseX, mouseY, entries, listTop, listBottom, visibleRows);
        if (hoveredEntry != null) {
            graphics.renderTooltip(font, Component.literal(previewLabel(hoveredEntry)), mouseX, mouseY);
        }
    }

    private FormattedCharSequence truncate(Font font, TextIndexStore.Entry e, int maxWidth) {
        String label = previewLabel(e).replace('\n', ' ').replace('\r', ' ');
        if (font.width(label) > maxWidth) {
            int chars = label.length();
            while (chars > 0 && font.width(label.substring(0, chars) + "…") > maxWidth) {
                chars--;
            }
            label = label.substring(0, chars) + "…";
        }
        return Component.literal(label).getVisualOrderText();
    }

    private static String previewLabel(TextIndexStore.Entry e) {
        if (e.shared()) {
            return "[shared] " + e.preview();
        }
        String p = e.preview();
        return (p == null || p.isEmpty()) ? "(empty)" : p;
    }

    private TextIndexStore.Entry entryAt(int mouseX, int mouseY, List<TextIndexStore.Entry> entries,
                                          int listTop, int listBottom, int visibleRows) {
        int x = getX();
        int w = getWidth();
        if (mouseX < x + 3 || mouseX >= x + w - CONTENT_RIGHT_PAD) return null;
        if (mouseY < listTop || mouseY >= listBottom) return null;
        int row = (mouseY - listTop) / ROW_HEIGHT;
        if (row < 0 || row >= visibleRows) return null;
        int index = row + scrollOffset;
        if (index < 0 || index >= entries.size()) return null;
        return entries.get(index);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!shouldShow() || button != 0) return false;
        if (!isMouseOver(mouseX, mouseY)) return false;
        List<TextIndexStore.Entry> entries = currentEntries();
        if (entries.isEmpty()) return false;

        int y = getY();
        int h = getHeight();
        int listTop = y + HEADER_HEIGHT + 4;
        int listBottom = y + h - 4;
        int visibleRows = Math.max(0, (listBottom - listTop) / ROW_HEIGHT);
        TextIndexStore.Entry e = entryAt((int) mouseX, (int) mouseY, entries, listTop, listBottom, visibleRows);
        if (e == null) return false;
        ponderUi.seekToTime(e.tick());
        playDownSound(Minecraft.getInstance().getSoundManager());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!shouldShow()) return false;
        if (!isMouseOver(mouseX, mouseY)) return false;
        List<TextIndexStore.Entry> entries = currentEntries();
        if (entries.isEmpty()) return false;

        int listTop = getY() + HEADER_HEIGHT + 4;
        int listBottom = getY() + getHeight() - 4;
        int visibleRows = Math.max(0, (listBottom - listTop) / ROW_HEIGHT);
        int maxScroll = Math.max(0, entries.size() - visibleRows);
        if (maxScroll <= 0) return true;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return shouldShow() && super.isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean isFocused() {
        return false;
    }

    @Override
    public void setFocused(boolean focused) {
        // never focusable
    }

    private boolean shouldShow() {
        if (!this.visible) return false;
        if (!VISIBLE) return false;
        if (PickState.isActive() || InterfaceSlotEditState.isActive()) return false;
        PonderScene active = ponderUi.getActiveScene();
        return active != null;
    }

    private List<TextIndexStore.Entry> currentEntries() {
        PonderScene active = ponderUi.getActiveScene();
        return active == null ? List.of() : TextIndexStore.get(active);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        // panel is purely visual; no narration
    }
}
