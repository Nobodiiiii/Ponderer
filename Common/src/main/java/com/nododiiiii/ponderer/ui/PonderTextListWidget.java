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
 * Entries are populated by {@link com.nododiiiii.ponderer.ponder.TextMarkerInstruction}
 * during scene begin() and exposed through {@link TextIndexStore}.
 */
public class PonderTextListWidget extends AbstractWidget {

    public static final int PANEL_WIDTH = 140;
    public static final int RIGHT_MARGIN = 4;
    public static final int TOP_Y = 70;
    public static final int BOTTOM_PAD = 60; // leave room for bottom button row

    private static final int ROW_HEIGHT = 11;
    private static final int HEADER_HEIGHT = 14;
    private static final int PADDING_X = 6;

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
        // Frame
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xC0_5a4520);
        graphics.fill(x, y, x + w, y + h, 0xE6_120E08);

        Font font = Minecraft.getInstance().font;
        String header = "Texts (" + entries.size() + ")";
        graphics.drawString(font, header, x + PADDING_X, y + 3, 0xF8E5B0, false);
        graphics.fill(x + 2, y + HEADER_HEIGHT, x + w - 2, y + HEADER_HEIGHT + 1, 0x60_8a6a30);

        int listTop = y + HEADER_HEIGHT + 3;
        int listBottom = y + h - 3;
        int visibleRows = Math.max(0, (listBottom - listTop) / ROW_HEIGHT);
        int maxScroll = Math.max(0, entries.size() - visibleRows);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        int currentSceneTime = ponderUi.getActiveScene().getCurrentTime();
        int textWidth = w - PADDING_X * 2 - 4;

        graphics.enableScissor(x, listTop, x + w, listBottom);
        for (int row = 0; row < visibleRows; row++) {
            int index = row + scrollOffset;
            if (index >= entries.size()) break;
            TextIndexStore.Entry e = entries.get(index);

            int rowY = listTop + row * ROW_HEIGHT;
            boolean played = e.tick() <= currentSceneTime;
            boolean hovered = mouseX >= x + 2 && mouseX < x + w - 2
                && mouseY >= rowY - 1 && mouseY < rowY + ROW_HEIGHT - 1;

            if (hovered) {
                graphics.fill(x + 2, rowY - 1, x + w - 2, rowY + ROW_HEIGHT - 1, 0x70_5a4520);
            }
            // Accent bar on the left of each row
            int accent = played ? 0xFF_F8C24A : 0xFF_555049;
            graphics.fill(x + 2, rowY, x + 4, rowY + ROW_HEIGHT - 2, accent);

            int textColor = played ? (hovered ? 0xFFFFE89A : 0xFFE0D8B0) : 0xFF888070;
            FormattedCharSequence line = truncate(font, e, textWidth);
            graphics.drawString(font, line, x + PADDING_X, rowY + 1, textColor, false);
        }
        graphics.disableScissor();

        // Scroll bar if needed
        if (entries.size() > visibleRows && visibleRows > 0) {
            int trackTop = listTop;
            int trackBottom = listBottom;
            int trackH = trackBottom - trackTop;
            int thumbH = Math.max(10, trackH * visibleRows / entries.size());
            int thumbY = trackTop + (trackH - thumbH) * scrollOffset / Math.max(1, maxScroll);
            int barX = x + w - 4;
            graphics.fill(barX, trackTop, barX + 2, trackBottom, 0x40_8a6a30);
            graphics.fill(barX, thumbY, barX + 2, thumbY + thumbH, 0xC0_F8C24A);
        }

        // Tooltip with full text on hover
        TextIndexStore.Entry hoveredEntry = entryAt(mouseX, mouseY, entries, listTop, listBottom, visibleRows);
        if (hoveredEntry != null) {
            String full = previewLabel(hoveredEntry);
            graphics.renderTooltip(font, Component.literal(full), mouseX, mouseY);
        }
    }

    private FormattedCharSequence truncate(Font font, TextIndexStore.Entry e, int maxWidth) {
        String label = previewLabel(e);
        // Strip newlines for the list display
        label = label.replace('\n', ' ').replace('\r', ' ');
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
        if (mouseX < x + 2 || mouseX >= x + w - 2) return null;
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
        int listTop = y + HEADER_HEIGHT + 3;
        int listBottom = y + h - 3;
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

        int listTop = getY() + HEADER_HEIGHT + 3;
        int listBottom = getY() + getHeight() - 3;
        int visibleRows = Math.max(0, (listBottom - listTop) / ROW_HEIGHT);
        int maxScroll = Math.max(0, entries.size() - visibleRows);
        if (maxScroll <= 0) return true; // still consume to prevent scene switch
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
