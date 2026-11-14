package com.nododiiiii.ponderer.runtime.mixin;

import com.nododiiiii.ponderer.runtime.render.PonderInterpolationSnapshot;
import net.createmod.catnip.animation.LerpedFloat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LerpedFloat.class)
public class LerpedFloatInterpolationMixin {
    @Inject(method = "tickChaser", at = @At("HEAD"), remap = false)
    private void ponderer$captureBatchTick(CallbackInfo ci) {
        PonderInterpolationSnapshot.captureFloat((LerpedFloat) (Object) this);
    }

    @Inject(method = {"setValue", "setValueNoUpdate"}, at = @At("HEAD"), remap = false)
    private void ponderer$captureBatchValue(double value, CallbackInfo ci) {
        PonderInterpolationSnapshot.captureFloat((LerpedFloat) (Object) this);
    }

    @Inject(method = "startWithValue", at = @At("RETURN"), remap = false)
    private void ponderer$resetBatchStart(double value, CallbackInfoReturnable<LerpedFloat> cir) {
        PonderInterpolationSnapshot.resetFloat((LerpedFloat) (Object) this);
    }
}
