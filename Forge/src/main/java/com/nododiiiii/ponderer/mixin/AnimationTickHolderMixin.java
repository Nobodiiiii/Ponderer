package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.projector.client.ProjectorRenderContext;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnimationTickHolder.class)
public class AnimationTickHolderMixin {

    @Inject(method = "getTicks()I", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ponderer$getTicks(CallbackInfoReturnable<Integer> cir) {
        setFrameTick(cir);
    }

    @Inject(method = "getTicks(Z)I", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ponderer$getTicksPaused(boolean includePaused, CallbackInfoReturnable<Integer> cir) {
        setFrameTick(cir);
    }

    @Inject(
        method = "getTicks(Lnet/minecraft/world/level/LevelAccessor;)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 0)
    private static void ponderer$getTicksForLevel(LevelAccessor level, CallbackInfoReturnable<Integer> cir) {
        setFrameTick(cir);
    }

    @Inject(method = "getPartialTicks()F", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ponderer$getPartialTicks(CallbackInfoReturnable<Float> cir) {
        setPartialTick(cir);
    }

    @Inject(
        method = "getPartialTicks(Lnet/minecraft/world/level/LevelAccessor;)F",
        at = @At("HEAD"),
        cancellable = true,
        require = 0)
    private static void ponderer$getPartialTicksForLevel(LevelAccessor level, CallbackInfoReturnable<Float> cir) {
        setPartialTick(cir);
    }

    @Inject(method = "getRenderTime()F", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ponderer$getRenderTime(CallbackInfoReturnable<Float> cir) {
        setRenderTime(cir);
    }

    @Inject(
        method = "getRenderTime(Lnet/minecraft/world/level/LevelAccessor;)F",
        at = @At("HEAD"),
        cancellable = true,
        require = 0)
    private static void ponderer$getRenderTimeForLevel(LevelAccessor level, CallbackInfoReturnable<Float> cir) {
        setRenderTime(cir);
    }

    private static void setFrameTick(CallbackInfoReturnable<Integer> cir) {
        if (ProjectorRenderContext.hasFrameTime()) {
            cir.setReturnValue(ProjectorRenderContext.frameTick());
        }
    }

    private static void setPartialTick(CallbackInfoReturnable<Float> cir) {
        if (ProjectorRenderContext.hasFrameTime()) {
            cir.setReturnValue(ProjectorRenderContext.partialTick());
        }
    }

    private static void setRenderTime(CallbackInfoReturnable<Float> cir) {
        if (ProjectorRenderContext.hasFrameTime()) {
            cir.setReturnValue(ProjectorRenderContext.renderTime());
        }
    }
}
