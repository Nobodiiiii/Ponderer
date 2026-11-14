package com.nododiiiii.ponderer.runtime.api;

import com.nododiiiii.ponderer.runtime.spi.PlaybackUiExtension;
import net.minecraft.client.gui.screens.Screen;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PlaybackUiExtensions {
    private static final CopyOnWriteArrayList<PlaybackUiExtension> EXTENSIONS = new CopyOnWriteArrayList<>();
    private PlaybackUiExtensions() {}
    public static AutoCloseable install(PlaybackUiExtension extension) {
        EXTENSIONS.add(extension);
        return () -> EXTENSIONS.remove(extension);
    }
    public static boolean keyPressed(Screen screen, int key, int scanCode, int modifiers) {
        return EXTENSIONS.stream().anyMatch(extension -> extension.keyPressed(screen, key, scanCode, modifiers));
    }
}
