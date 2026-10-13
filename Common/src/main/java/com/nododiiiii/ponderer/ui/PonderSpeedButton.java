package com.nododiiiii.ponderer.ui;

import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class PonderSpeedButton extends PonderButton {
    private final String label;

    public PonderSpeedButton(int x, int y, String label, Component description, Runnable callback) {
        super(x, y);
        this.label = label;
        setMessage(description);
        withCallback(callback);
        enableFade(0, 5);
    }

    @Override
    public void doRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.doRender(graphics, mouseX, mouseY, partialTicks);
        float opacity = fade.getValue(partialTicks);
        if (opacity < .1f) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, z + 10);
        int color = ((int) (opacity * 255) << 24) | 0xFFFFFF;
        graphics.drawCenteredString(Minecraft.getInstance().font, label,
            getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, color);
        graphics.pose().popPose();
    }
}
