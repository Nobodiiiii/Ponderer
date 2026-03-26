package com.nododiiiii.ponderer.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Resolve the UI viewport used by embedded mirror screens in Ponder.
 * Falls back to full GUI size when mirror bounds are unavailable.
 */
public final class UiAnchorViewport {

    private UiAnchorViewport() {
    }

    public record Rect(double left, double top, double width, double height) {
        public boolean isValid() {
            return width > 1 && height > 1;
        }
    }

    public static Rect resolve(Minecraft mc) {
        int guiW = Math.max(1, mc.getWindow().getGuiScaledWidth());
        int guiH = Math.max(1, mc.getWindow().getGuiScaledHeight());

        Screen mirror = resolveEmbeddedMirrorScreen();
        if (mirror instanceof AbstractContainerScreen<?> container) {
            Rect rect = readContainerRect(container, guiW, guiH);
            if (rect != null && rect.isValid()) {
                return rect;
            }
        }

        return new Rect(0.0, 0.0, guiW, guiH);
    }

    @Nullable
    private static Rect readContainerRect(AbstractContainerScreen<?> container, int guiW, int guiH) {
        try {
            Field leftPos = AbstractContainerScreen.class.getDeclaredField("leftPos");
            Field topPos = AbstractContainerScreen.class.getDeclaredField("topPos");
            Field imageWidth = AbstractContainerScreen.class.getDeclaredField("imageWidth");
            Field imageHeight = AbstractContainerScreen.class.getDeclaredField("imageHeight");
            leftPos.setAccessible(true);
            topPos.setAccessible(true);
            imageWidth.setAccessible(true);
            imageHeight.setAccessible(true);

            double width = imageWidth.getInt(container);
            double height = imageHeight.getInt(container);
            if (width <= 1 || height <= 1) {
                return null;
            }

            double left = leftPos.getInt(container);
            double top = topPos.getInt(container);

            // During async mirror attach, init may not have stabilized left/top yet.
            // If we observe the default (0,0) on a larger GUI, derive centered bounds.
            if (left == 0.0 && top == 0.0 && (guiW > width || guiH > height)) {
                left = Math.floor((guiW - width) * 0.5);
                top = Math.floor((guiH - height) * 0.5);
            }
            return new Rect(left, top, width, height);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static Screen resolveEmbeddedMirrorScreen() {
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