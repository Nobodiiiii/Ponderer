package com.nododiiiii.ponderer.runtime.interfaces.sandbox;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;

import javax.annotation.Nullable;

/**
 * Swaps {@code Minecraft.player} and {@code Minecraft.gameMode} to the sandbox objects for the
 * duration of one call into sandbox menu/screen code, then restores them and runs the
 * tripwire. Render thread only; re-entrant for the same session.
 *
 * <p>{@code Minecraft.level}, {@code Minecraft.screen} and the connection are deliberately not
 * swapped: the level field is typed {@code ClientLevel}, the screen must stay the host, and
 * every packet sent through the real connection meanwhile is dropped by the firewall.
 * {@link SandboxWorldBridge} redirects world/chunk lookups to the sandbox during this scope.
 */
public final class SandboxScope implements AutoCloseable {

    private static final SandboxScope HANDLE = new SandboxScope();

    @Nullable
    private static SandboxSession active;
    private static int depth;
    @Nullable
    private static LocalPlayer realPlayer;
    @Nullable
    private static MultiPlayerGameMode realGameMode;

    private SandboxScope() {
    }

    public static SandboxScope enter(SandboxSession session) {
        RenderSystem.assertOnRenderThread();
        if (depth == 0) {
            Minecraft mc = Minecraft.getInstance();
            realPlayer = mc.player;
            realGameMode = mc.gameMode;
            active = session;
            session.tripwire().arm(realPlayer, mc.screen);
            mc.player = session.player();
            mc.gameMode = session.gameMode();
        } else if (active != session) {
            throw new IllegalStateException("nested sandbox sessions are not supported");
        }
        depth++;
        return HANDLE;
    }

    @Override
    public void close() {
        if (depth <= 0) {
            return;
        }
        if (--depth > 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.player = realPlayer;
        mc.gameMode = realGameMode;
        SandboxSession session = active;
        active = null;
        realPlayer = null;
        realGameMode = null;
        if (session != null) {
            session.tripwire().verify(session, mc.player, mc.screen);
        }
    }

    public static boolean isActive() {
        return depth > 0;
    }

    @Nullable
    public static SandboxSession activeSession() {
        return active;
    }

    /** The real local player while a scope is active (null outside a scope). */
    @Nullable
    public static LocalPlayer realPlayer() {
        return realPlayer;
    }
}
