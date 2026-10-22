package com.nododiiiii.ponderer.interfaces.sandbox;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Decision logic behind the {@code Connection#send} injection.
 *
 * <p>Rule 1: while a sandbox scope is active on the render thread every outbound packet is
 * handed to the session and dropped. Rule 2: outside a scope, vanilla container packets
 * carrying a sandbox window id (101–127, disjoint from the server's 1–100 counter) are
 * dropped, catching sandbox work a mod deferred past the scope.
 */
public final class SandboxNetworkFirewall {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int WINDOW_ID_MIN = 101;
    public static final int WINDOW_ID_MAX = 127;

    private static volatile boolean live;
    private static final AtomicLong INTERCEPTED = new AtomicLong();
    private static final AtomicLong TAINT_DROPS = new AtomicLong();

    private SandboxNetworkFirewall() {
    }

    /** Called from the injection on every send: proves the injection is applied. */
    public static void markLive() {
        live = true;
    }

    public static boolean isLive() {
        return live;
    }

    /** Returns true when {@code packet} must not reach the real connection. */
    public static boolean shouldBlock(Packet<?> packet) {
        if (SandboxScope.isActive() && RenderSystem.isOnRenderThread()) {
            INTERCEPTED.incrementAndGet();
            SandboxSession session = SandboxScope.activeSession();
            if (session != null) {
                session.onOutbound(packet);
            }
            return true;
        }
        if (isTaintedContainerPacket(packet)) {
            TAINT_DROPS.incrementAndGet();
            LOGGER.warn("[Ponderer] Dropped deferred sandbox packet {} outside sandbox scope",
                packet.getClass().getSimpleName());
            return true;
        }
        return false;
    }

    public static boolean isSandboxWindowId(int windowId) {
        return windowId >= WINDOW_ID_MIN && windowId <= WINDOW_ID_MAX;
    }

    public static boolean isTaintedContainerPacket(Packet<?> packet) {
        int windowId;
        if (packet instanceof ServerboundContainerClickPacket click) {
            windowId = click.getContainerId();
        } else if (packet instanceof ServerboundContainerClosePacket close) {
            windowId = close.getContainerId();
        } else if (packet instanceof ServerboundContainerButtonClickPacket button) {
            windowId = button.getContainerId();
        } else if (packet instanceof ServerboundPlaceRecipePacket recipe) {
            windowId = recipe.getContainerId();

        } else {
            return false;
        }
        return isSandboxWindowId(windowId);
    }

    public static long interceptedCount() {
        return INTERCEPTED.get();
    }

    public static long taintDropCount() {
        return TAINT_DROPS.get();
    }
}
