package com.nododiiiii.ponderer.runtime.interfaces.sandbox;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import javax.annotation.Nullable;

/** World access by mod GUI code is redirected only on the render thread in a sandbox dispatch. */
public final class SandboxWorldBridge {
    private SandboxWorldBridge() {}

    @Nullable
    public static SandboxLevel level(Object receiver) {
        if (!SandboxScope.isActive() || !RenderSystem.isOnRenderThread()) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (receiver != mc.level && receiver != mc.level.getChunkSource())) return null;
        SandboxSession session = SandboxScope.activeSession();
        return session == null ? null : session.level();
    }
}
