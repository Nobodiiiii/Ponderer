package com.nododiiiii.ponderer.runtime.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.element.WorldSectionElementImpl;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/**
 * Prevents NullPointerException when erase() or add() is called on a
 * WorldSectionElement whose internal Selection has not been initialized
 * (e.g. hide_section executed before any show_structure).
 * Also defers translucent section geometry to Ponder's late render buffer.
 */
@Mixin(WorldSectionElementImpl.class)
public class WorldSectionElementImplMixin {

    @Shadow(remap = false)
    @Nullable
    Selection section;

    @Inject(method = "erase", at = @At("HEAD"), cancellable = true, remap = false)
    private void ponderer$safeErase(Selection toErase, CallbackInfo ci) {
        if (section == null) {
            ci.cancel();
        }
    }

    @Inject(method = "add", at = @At("HEAD"), cancellable = true, remap = false)
    private void ponderer$safeAdd(Selection toAdd, CallbackInfo ci) {
        if (section == null) {
            ci.cancel();
        }
    }

    @WrapOperation(
        method = "renderLayer(Lnet/createmod/ponder/api/level/PonderLevel;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/gui/GuiGraphics;FF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;",
            remap = true),
        remap = true)
    private VertexConsumer ponderer$delayTranslucentSections(MultiBufferSource buffer, RenderType type,
                                                           Operation<VertexConsumer> original) {
        if (type == RenderType.translucent() && buffer instanceof SuperRenderTypeBuffer superBuffer) {
            return superBuffer.getLateBuffer(type);
        }
        return original.call(buffer, type);
    }
}
