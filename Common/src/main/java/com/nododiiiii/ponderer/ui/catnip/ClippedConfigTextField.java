package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.config.ui.ConfigTextField;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class ClippedConfigTextField extends ConfigTextField {

    private static final int HINT_COLOR = 0x90_A0A0A0;
    private boolean consumeRightClick = false;

    public ClippedConfigTextField(Font font, int x, int y, int width, int height) {
        super(font, x, y, width, height);
    }

    public void setConsumeRightClick(boolean consumeRightClick) {
        this.consumeRightClick = consumeRightClick;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (consumeRightClick && button == 1 && isMouseOver(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        String savedHint = this.hint;
        this.hint = "";
        super.renderWidget(graphics, mouseX, mouseY, partialTicks);
        this.hint = savedHint;

        if (hint == null || hint.isEmpty() || !getValue().isEmpty()) {
            return;
        }

        int maxWidth = Math.max(0, getWidth() - 10);
        String clipped = font.plainSubstrByWidth(hint, maxWidth);
        graphics.drawString(font, clipped, getX() + 5, this.getY() + (this.height - 8) / 2, HINT_COLOR);
    }
}
