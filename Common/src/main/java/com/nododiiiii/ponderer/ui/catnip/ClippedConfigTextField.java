package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.config.ui.ConfigTextField;
import net.createmod.catnip.gui.UIRenderHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class ClippedConfigTextField extends ConfigTextField {

    public ClippedConfigTextField(Font font, int x, int y, int width, int height) {
        super(font, x, y, width, height);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderWidget(graphics, mouseX, mouseY, partialTicks);

        if (hint == null || hint.isEmpty() || !getValue().isEmpty()) {
            return;
        }

        int maxWidth = Math.max(0, getWidth() - 10);
        String clipped = font.plainSubstrByWidth(hint, maxWidth);
        graphics.drawString(font, clipped, getX() + 5, this.getY() + (this.height - 8) / 2,
            UIRenderHelper.COLOR_TEXT.getFirst().scaleAlpha(.75f).getRGB());
    }
}
