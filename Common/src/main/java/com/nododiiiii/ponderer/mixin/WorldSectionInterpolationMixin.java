package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.runtime.core.PonderInterpolationSnapshot;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.foundation.element.WorldSectionElementImpl;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.ponder.foundation.ui.PonderUI;
import com.nododiiiii.ponderer.ui.PonderUiPreviewAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldSectionElementImpl.class)
public class WorldSectionInterpolationMixin {
    @Redirect(method = "reverseTransformVec", at = @At(value = "INVOKE",
        target = "Lnet/createmod/catnip/animation/AnimationTickHolder;getPartialTicks()F", remap = false))
    private float ponderer$freezePreviewPicking() {
        if (Minecraft.getInstance().screen instanceof PonderUI ui) {
            var preview = ((PonderUiPreviewAccess) ui).ponderer$getFreePreview();
            if (preview.isActive()) {
                return preview.partialTicks();
            }
        }
        return AnimationTickHolder.getPartialTicks();
    }

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
