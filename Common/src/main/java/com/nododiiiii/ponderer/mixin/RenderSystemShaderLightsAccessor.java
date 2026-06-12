package com.nododiiiii.ponderer.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the level diffuse light directions so off-screen rendering (e.g. the projector
 * show_controls panel) can snapshot them before invoking GUI/item lighting and restore them
 * afterwards, keeping world entity lighting untouched.
 */
@Mixin(RenderSystem.class)
public interface RenderSystemShaderLightsAccessor {

    @Accessor("shaderLightDirections")
    static Vector3f[] ponderer$getShaderLightDirections() {
        throw new AssertionError();
    }
}
