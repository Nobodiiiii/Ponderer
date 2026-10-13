package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.ponder.PonderInterpolationSnapshot;
import net.createmod.catnip.animation.LerpedFloat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LerpedFloat.class)
public class LerpedFloatResetMixin {
    @Inject(method = "startWithValue", at = @At("RETURN"), remap = false)
    private void ponderer$resetBatchStart(double value, CallbackInfoReturnable<LerpedFloat> cir) {
        PonderInterpolationSnapshot.resetFloat((LerpedFloat) (Object) this);
    }
}
