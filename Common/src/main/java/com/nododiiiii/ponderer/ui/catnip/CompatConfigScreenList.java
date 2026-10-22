package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.config.ui.ConfigScreenList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Bridges the 1.21 list rectangle API to the identical 1.20 viewport. */
public class CompatConfigScreenList extends ConfigScreenList {
    public CompatConfigScreenList(Minecraft client, int width, int height, int top, int entryHeight) {
        super(client, width, height, top, top + height, entryHeight);
    }
    public int getX() { return x0; }
    public int getY() { return y0; }
    public int getRight() { return x1; }
    public int getBottom() { return y1; }
    public int getHeight() { return y1 - y0; }
    public void setX(int x) { setLeftPos(x); }
    public void updateSizeAndPosition(int width, int height, int top) { updateSize(width, height, top, top + height); }
    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        renderWidget(graphics, x, y, partialTick);
    }
    public void renderWidget(GuiGraphics graphics, int x, int y, float partialTick) {
        super.render(graphics, x, y, partialTick);
    }
}
