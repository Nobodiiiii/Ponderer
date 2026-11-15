package com.nododiiiii.ponderer.runtime.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nododiiiii.ponderer.runtime.render.PonderRenderCache;
import com.nododiiiii.ponderer.runtime.render.PonderAnimatedTextures;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.element.WorldSectionElementImpl;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cache tracking has an independent capability from section safety. */
@Mixin(WorldSectionElementImpl.class)
public class WorldSectionRenderCacheMixin {
    @Inject(method = "renderLayer", at = @At("HEAD"))
    private void ponderer$trackRenderCache(PonderLevel world, MultiBufferSource buffer, RenderType layer,
                                         GuiGraphics graphics, float fade, float partialTicks, CallbackInfo ci) {
        PonderRenderCache.record(world, (WorldSectionElementImpl) (Object) this, layer);
    }

    @WrapOperation(method = "renderLayer", at = @At(value = "INVOKE",
        target = "Lnet/createmod/catnip/render/SuperByteBuffer;renderInto(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"))
    private void ponderer$trackAnimatedTextures(SuperByteBuffer buffer, PoseStack pose, VertexConsumer target,
                                              Operation<Void> original) {
        original.call(buffer, pose, PonderAnimatedTextures.track(buffer, target));
    }
}
