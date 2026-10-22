package com.nododiiiii.ponderer.ui;

import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class SoftHintTextFieldWidget extends HintableTextFieldWidget {

    /** Dim gray hint color - clearly distinguishable from actual input text. */
    private static final int SOFT_HINT_COLOR = 0xFF_505050;
    private final Font hintFont;
    private String softHint;

    public SoftHintTextFieldWidget(Font font, int x, int y, int width, int height) {
        super(font, x, y, width, height);
        this.hintFont = font;
    }

    public void setHint(String hint) {
        setHint(hint == null ? null : Component.literal(hint));
    }

    @Override
    public void setHint(Component hint) {
        // Use the Component API shared by old and new Ponder versions.
        softHint = hint == null ? null : hint.getString();
        super.setHint(Component.empty());
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderWidget(graphics, mouseX, mouseY, partialTicks);

        if (softHint == null || softHint.isEmpty())
            return;

        if (!getValue().isEmpty())
            return;

        // Clip hint to fit within the field, leaving padding on both sides
        int maxW = getWidth() - 10;
        String displayHint = softHint;
        if (hintFont.width(displayHint) > maxW) {
            displayHint = hintFont.plainSubstrByWidth(displayHint, maxW - hintFont.width("...")) + "...";
        }
        graphics.drawString(hintFont, displayHint, getX() + 5, getY() + (getHeight() - 8) / 2, SOFT_HINT_COLOR);
    }
}
