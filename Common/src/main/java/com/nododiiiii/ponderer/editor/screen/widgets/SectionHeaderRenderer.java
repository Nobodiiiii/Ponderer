package com.nododiiiii.ponderer.editor.screen.widgets;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Shared section heading colors and spacing for button rows and compact item grid rows. */
public final class SectionHeaderRenderer {

    static final int TEXT_COLOR = 0xFFCCCC77;
    private static final int HIGHLIGHT_COLOR = 0xFFF3D46B;
    private static final int LINE_COLOR = 0x40FFFFFF;
    private static final int TEXT_LINE_GAP = 2;
    private static final int BOTTOM_PADDING = 2;

    private SectionHeaderRenderer() {
    }

    private static int lineY(int rowY, int rowHeight) {
        int buttonBottom = EntryTextSupport.fullButtonY(rowY, rowHeight)
            + EntryTextSupport.fullButtonHeight(rowHeight);
        return Math.min(rowY + rowHeight, buttonBottom) - BOTTOM_PADDING - 1;
    }

    public static int textY(Font font, int rowY, int rowHeight) {
        return lineY(rowY, rowHeight) - TEXT_LINE_GAP - font.lineHeight;
    }

    public static void render(GuiGraphics graphics, Font font, String title, int left, int right,
                              int rowY, int rowHeight, boolean highlighted) {
        graphics.drawString(font, font.plainSubstrByWidth(title, Math.max(0, right - left)),
            left, textY(font, rowY, rowHeight), highlighted ? HIGHLIGHT_COLOR : TEXT_COLOR);
        int lineY = lineY(rowY, rowHeight);
        graphics.fill(left, lineY, right, lineY + 1, LINE_COLOR);
    }
}
