package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.config.ui.ConfigTextField;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class ClippedConfigTextField extends ConfigTextField {

    private static final int HINT_COLOR = 0x90_A0A0A0;
    private final Font hintFont;
    private String clippedHint;
    private boolean consumeRightClick = false;

    public ClippedConfigTextField(Font font, int x, int y, int width, int height) {
        super(font, x, y, width, height);
        this.hintFont = font;
    }

    public void setHint(String hint) {
        setHint(hint == null ? null : Component.literal(hint));
    }

    @Override
    public void setHint(Component hint) {
        // Ponder 1.0.87 removed the String hint field and setter. The Component API
        // is also available through EditBox in older versions.
        clippedHint = hint == null ? null : hint.getString();
        super.setHint(Component.empty());
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
        super.renderWidget(graphics, mouseX, mouseY, partialTicks);

        if (clippedHint == null || clippedHint.isEmpty() || !getValue().isEmpty()) {
            return;
        }

        int maxWidth = Math.max(0, getWidth() - 10);
        String clipped = hintFont.plainSubstrByWidth(clippedHint, maxWidth);
        graphics.drawString(hintFont, clipped, getX() + 5, getY() + (getHeight() - 8) / 2, HINT_COLOR);
    }
}
