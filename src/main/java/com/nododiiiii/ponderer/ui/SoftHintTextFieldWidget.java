package com.nododiiiii.ponderer.ui;

import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class SoftHintTextFieldWidget extends HintableTextFieldWidget {

    private static final int SOFT_HINT_COLOR = 0x11111111;

    public SoftHintTextFieldWidget(Font font, int x, int y, int width, int height) {
        super(font, x, y, width, height);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderWidget(graphics, mouseX, mouseY, partialTicks);

        if (hint == null || hint.isEmpty())
            return;

        if (!getValue().isEmpty())
            return;

        graphics.drawString(font, hint, getX() + 5, this.getY() + (this.height - 8) / 2, SOFT_HINT_COLOR);
    }
}
