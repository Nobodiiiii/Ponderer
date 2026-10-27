package com.nododiiiii.ponderer.runtime.spi;

import net.minecraft.client.gui.screens.Screen;

@FunctionalInterface
public interface PlaybackUiExtension {
    boolean keyPressed(Screen screen, int key, int scanCode, int modifiers);
}
