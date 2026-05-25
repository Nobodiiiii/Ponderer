package com.nododiiiii.ponderer.ui;

import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;

/**
 * Thread-local capture window for {@code Minecraft.setScreen} calls.
 *
 * <p>Used by the held-item {@code show_interface} flow: tetra-style items implement
 * their GUI as {@code if (level.isClientSide) Minecraft.getInstance().setScreen(...)}
 * with no server-side menu open. To mirror those into PonderUI we run {@code Item.use}
 * on the client, intercept the setScreen call via a mixin, and embed the captured
 * Screen instead of letting it replace PonderUI.
 */
public final class ClientScreenCapture {

    private static boolean capturing = false;
    @Nullable
    private static Screen captured;
    private static boolean discardActiveScreen = false;

    private ClientScreenCapture() {
    }

    public static void begin(boolean discardActiveScreen) {
        capturing = true;
        captured = null;
        ClientScreenCapture.discardActiveScreen = discardActiveScreen;
    }

    public static void end() {
        capturing = false;
        captured = null;
        discardActiveScreen = false;
    }

    public static boolean isCapturing() {
        return capturing;
    }

    public static boolean shouldDiscardActiveScreen() {
        return discardActiveScreen;
    }

    public static void offer(@Nullable Screen screen) {
        if (capturing && screen != null) {
            captured = screen;
        }
    }

    @Nullable
    public static Screen drain() {
        Screen result = captured;
        captured = null;
        return result;
    }
}
