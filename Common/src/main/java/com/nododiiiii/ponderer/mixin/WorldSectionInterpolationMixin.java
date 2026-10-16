package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.ponder.PonderInterpolationSnapshot;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.foundation.element.WorldSectionElementImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldSectionElementImpl.class)
public class WorldSectionInterpolationMixin {
    @Inject(method = "setAnimatedOffset", at = @At("HEAD"))
    private void ponderer$captureBatchOffset(Vec3 offset, boolean force, CallbackInfo ci) {
        PonderInterpolationSnapshot.captureSectionOffset((WorldSectionElement) (Object) this);
    }

    @Inject(method = "setAnimatedOffset", at = @At("RETURN"))
    private void ponderer$resetBatchOffset(Vec3 offset, boolean force, CallbackInfo ci) {
        if (force) {
            PonderInterpolationSnapshot.resetSectionOffset((WorldSectionElement) (Object) this);
        }
    }

    @Inject(method = "setAnimatedRotation", at = @At("HEAD"))
    private void ponderer$captureBatchRotation(Vec3 rotation, boolean force, CallbackInfo ci) {
        PonderInterpolationSnapshot.captureSectionRotation((WorldSectionElement) (Object) this);
    }

    @Inject(method = "setAnimatedRotation", at = @At("RETURN"))
    private void ponderer$resetBatchRotation(Vec3 rotation, boolean force, CallbackInfo ci) {
        if (force) {
            PonderInterpolationSnapshot.resetSectionRotation((WorldSectionElement) (Object) this);
        }
    }

    @Inject(method = "resetAnimatedTransform", at = @At("RETURN"), remap = false)
    private void ponderer$resetBatchTransform(CallbackInfo ci) {
        WorldSectionElement self = (WorldSectionElement) (Object) this;
        PonderInterpolationSnapshot.resetSectionOffset(self);
        PonderInterpolationSnapshot.resetSectionRotation(self);
    }
}
