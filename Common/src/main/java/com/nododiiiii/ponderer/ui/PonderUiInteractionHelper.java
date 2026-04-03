package com.nododiiiii.ponderer.ui;

import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

public final class PonderUiInteractionHelper {

    private PonderUiInteractionHelper() {
    }

    public static boolean hasPriorityPonderButtonAt(Screen screen, double mouseX, double mouseY) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof PonderButton button
                && button.isVisible()
                && button.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }
}
