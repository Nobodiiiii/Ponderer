package com.nododiiiii.ponderer.runtime.viewer;

import com.nododiiiii.ponderer.interfaces.host.InterfaceHost;
import com.nododiiiii.ponderer.interfaces.platform.InterfacePlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;

/**
 * Resolve the UI viewport of the interface shown in Ponder.
 * Falls back to a centered container viewport when no interface is visible.
 */
public final class UiAnchorViewport {

    private static final int DEFAULT_CONTAINER_WIDTH = 176;
    private static final int DEFAULT_CONTAINER_HEIGHT = 166;

    private UiAnchorViewport() {
    }

    public record Rect(double left, double top, double width, double height) {
        public boolean isValid() {
            return width > 1 && height > 1;
        }
    }

    public static Rect resolve(Minecraft mc) {
        int[] rect = InterfaceHost.activeRect();
        if (rect != null && rect[2] > 1 && rect[3] > 1) {
            return new Rect(rect[0], rect[1], rect[2], rect[3]);
        }
        return centeredContainerFallback(mc);
    }

    public static Rect resolveForScreen(Minecraft mc, @Nullable Screen screen) {
        if (screen != null) {
            int[] rect = InterfacePlatform.get().containerRect(screen);
            if (rect != null && rect[2] > 1 && rect[3] > 1) {
                return new Rect(rect[0], rect[1], rect[2], rect[3]);
            }
        }
        return centeredContainerFallback(mc);
    }

    public static Rect resolveJeiForScreen(Minecraft mc, @Nullable Screen screen) {
        return resolveForScreen(mc, screen);
    }

    private static Rect centeredContainerFallback(Minecraft mc) {
        int guiW = Math.max(1, mc.getWindow().getGuiScaledWidth());
        int guiH = Math.max(1, mc.getWindow().getGuiScaledHeight());
        double width = Math.min(guiW, DEFAULT_CONTAINER_WIDTH);
        double height = Math.min(guiH, DEFAULT_CONTAINER_HEIGHT);
        double left = Math.floor((guiW - width) * 0.5);
        double top = Math.floor((guiH - height) * 0.5);
        return new Rect(left, top, width, height);
    }

    /** The interface screen currently embedded in PonderUI, if any. */
    @Nullable
    public static Screen getEmbeddedMirrorScreen() {
        return InterfaceHost.activeScreen();
    }
}
