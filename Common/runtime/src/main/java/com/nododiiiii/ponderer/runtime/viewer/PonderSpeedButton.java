package com.nododiiiii.ponderer.runtime.viewer;

import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class PonderSpeedButton extends PonderButton {
    private Component label;

    /** Aligns the complete box around a native Ponder button's content origin. */
    public PonderSpeedButton(int contentX, int contentY, String label, Component description, Runnable callback) {
        this(contentX - 4, contentY - 4, 28, Component.literal(label), description, callback);
    }

    public PonderSpeedButton(int x, int y, int width, Component label, Component description, Runnable callback) {
        super(x, y, width, 18);
        box.withBorderOffset(-2);
        this.label = label;
        withPadding(0, 0);
        setMessage(description);
        withCallback(callback);
        enableFade(0, 5);
    }

    public void setLabel(Component label) {
        this.label = label;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return active && visible && mouseX >= getX() && mouseY >= getY()
            && mouseX < getX() + getWidth() && mouseY < getY() + getHeight();
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
