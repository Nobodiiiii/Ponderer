package com.nododiiiii.ponderer.runtime.interfaces.sandbox;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;

/**
 * Decision logic behind the {@code Minecraft#setScreen} injection. Inside a sandbox scope the
 * host screen can never be replaced or closed: {@code setScreen(null)} becomes a session close
 * request and any other screen is offered to the session's screen stack.
 */
public final class SandboxScreenGate {

    private static volatile boolean live;

    private SandboxScreenGate() {
    }

    public static void markLive() {
        live = true;
    }

    public static boolean isLive() {
        return live;
    }

    /** Returns true when the real {@code setScreen} call must be cancelled. */
    public static boolean intercept(@Nullable Screen screen) {
        if (!SandboxScope.isActive()) {
            return false;
        }
        SandboxSession session = SandboxScope.activeSession();
        if (session == null) {
            return true;
        }
        if (screen == null) {
            session.requestClose("setScreen(null)");
        } else if (isDenied(screen)) {
            session.notice(screen.getTitle());
        } else {
            session.offerScreen(screen);
        }
        return true;
    }

    private static boolean isDenied(Screen screen) {
        return screen instanceof ChatScreen
            || screen instanceof PauseScreen
            || screen instanceof ConfirmLinkScreen
            || screen instanceof ConfirmScreen
            || screen instanceof DisconnectedScreen
            || screen instanceof ReceivingLevelScreen;
    }
}
