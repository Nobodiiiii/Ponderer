package com.nododiiiii.ponderer.ui;

import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class SoftHintTextFieldWidget extends HintableTextFieldWidget {

    /** Dim gray hint color - clearly distinguishable from actual input text. */
    private static final int SOFT_HINT_COLOR = 0xFF_505050;

    public SoftHintTextFieldWidget(Font font, int x, int y, int width, int height) {
        super(font, x, y, width, height);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Suppress parent hint rendering by temporarily clearing it
        String savedHint = this.hint;
        this.hint = "";
        super.renderWidget(graphics, mouseX, mouseY, partialTicks);
        this.hint = savedHint;

        if (hint == null || hint.isEmpty())
            return;

        if (!getValue().isEmpty())
            return;

        graphics.enableScissor(getX() + 1, getY(), getX() + width - 1, getY() + height);
        graphics.drawString(font, hint, getX() + 5, this.getY() + (this.height - 8) / 2, SOFT_HINT_COLOR);
        graphics.disableScissor();
    }
}
