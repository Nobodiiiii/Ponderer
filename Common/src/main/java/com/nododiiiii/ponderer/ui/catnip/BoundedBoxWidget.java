package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.gui.widget.BoxWidget;

/** A box whose position, size, border and mouse bounds describe the same rectangle. */
public class BoundedBoxWidget extends BoxWidget {

    public BoundedBoxWidget() {
        this(0, 0);
    }

    public BoundedBoxWidget(int x, int y) {
        this(x, y, 24, 24);
    }

    public BoundedBoxWidget(int x, int y, int width, int height) {
        super(x, y, width, height);
        box.withBorderOffset(-2);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return active && visible && mouseX >= getX() && mouseY >= getY()
            && mouseX < getX() + getWidth() && mouseY < getY() + getHeight();
    }
}
