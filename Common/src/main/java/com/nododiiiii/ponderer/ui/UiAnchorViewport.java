package com.nododiiiii.ponderer.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Resolve the UI viewport used by embedded mirror screens in Ponder.
 * Falls back to a centered container viewport when mirror bounds are unavailable.
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
        return resolveForScreen(mc, getEmbeddedMirrorScreen());
    }

    public static Rect resolveForScreen(Minecraft mc, @Nullable Screen mirror) {
        int guiW = Math.max(1, mc.getWindow().getGuiScaledWidth());
        int guiH = Math.max(1, mc.getWindow().getGuiScaledHeight());

        if (mirror instanceof AbstractContainerScreen<?> container) {
            InterfaceSlotOverlayRenderer.ContainerBounds bounds = InterfaceSlotOverlayRenderer.readContainerBounds(container);
            return new Rect(bounds.left(), bounds.top(), bounds.width(), bounds.height());
        }

        return centeredContainerFallback(guiW, guiH);
    }

    private static Rect centeredContainerFallback(int guiW, int guiH) {
        double width = Math.min(guiW, DEFAULT_CONTAINER_WIDTH);
        double height = Math.min(guiH, DEFAULT_CONTAINER_HEIGHT);
        double left = Math.floor((guiW - width) * 0.5);
        double top = Math.floor((guiH - height) * 0.5);
        return new Rect(left, top, width, height);
    }

    @Nullable
    public static Screen getEmbeddedMirrorScreen() {
        Screen mirror = queryEmbeddedMirror(
            "com.nododiiiii.ponderer.forge.sticksnapshot.client.ClientInputHandler");
        if (mirror != null) {
            return mirror;
        }
        return queryEmbeddedMirror(
            "com.nododiiiii.ponderer.neoforge.sticksnapshot.client.ClientInputHandler");
    }

    @Nullable
    private static Screen queryEmbeddedMirror(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            Method getter = clazz.getMethod("getEmbeddedMirrorScreen");
            Object value = getter.invoke(null);
            if (value instanceof Screen screen) {
                return screen;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
