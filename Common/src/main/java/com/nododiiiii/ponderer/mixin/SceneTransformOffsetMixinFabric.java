package com.nododiiiii.ponderer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nododiiiii.ponderer.ponder.PonderSceneViewOffsetAccess;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.ponder.foundation.PonderScene;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PonderScene.SceneTransform.class)
public class SceneTransformOffsetMixinFabric {

    @Shadow(remap = false)
    @Final
    private PonderScene this$0;

    @Unique
    private boolean ponderer$handledTwoArgApply;

    @Unique
    private float ponderer$storedBaseScale = Float.NaN;

    @Inject(method = "apply(Lcom/mojang/blaze3d/vertex/PoseStack;)Lcom/mojang/blaze3d/vertex/PoseStack;", at = @At("HEAD"), require = 0, remap = false)
    private void ponderer$beginOneArgApply(PoseStack ms, CallbackInfoReturnable<PoseStack> cir) {
        ponderer$handledTwoArgApply = false;
        ponderer$applyScaleOverride(AnimationTickHolder.getPartialTicks(this$0.getWorld()));
    }

    @Inject(method = "apply(Lcom/mojang/blaze3d/vertex/PoseStack;)Lcom/mojang/blaze3d/vertex/PoseStack;", at = @At("TAIL"), require = 0, remap = false)
    private void ponderer$applyViewOffsetOneArg(PoseStack ms, CallbackInfoReturnable<PoseStack> cir) {
        if (ponderer$handledTwoArgApply) {
            return;
        }
        float pt = AnimationTickHolder.getPartialTicks(this$0.getWorld());
        ponderer$applyViewOffsetCommon(ms, pt);
    }

    @Inject(method = "apply(Lnet/minecraft/class_4587;F)Lnet/minecraft/class_4587;", at = @At("TAIL"), require = 0, remap = false)
    private void ponderer$applyViewOffsetTwoArg(PoseStack ms, float pt, CallbackInfoReturnable<PoseStack> cir) {
        ponderer$handledTwoArgApply = true;
        ponderer$applyScaleOverride(pt);
        ponderer$applyViewOffsetCommon(ms, pt);
    }

    @Inject(method = "apply(Lcom/mojang/blaze3d/vertex/PoseStack;)Lcom/mojang/blaze3d/vertex/PoseStack;", at = @At("RETURN"), require = 0, remap = false)
    private void ponderer$endOneArgApply(PoseStack ms, CallbackInfoReturnable<PoseStack> cir) {
        ponderer$handledTwoArgApply = false;
    }

    private void ponderer$applyScaleOverride(float pt) {
        PonderSceneViewOffsetAccess access = (PonderSceneViewOffsetAccess) this$0;
        PonderSceneAccessor scene = (PonderSceneAccessor) this$0;
        if (access.ponderer$isScaleOverrideActive()) {
            if (Float.isNaN(ponderer$storedBaseScale)) {
                ponderer$storedBaseScale = scene.ponderer$getScaleFactor();
            }
            scene.ponderer$setScaleFactor(access.ponderer$getScaleOverride().getValue(pt));
            return;
        }
        if (!Float.isNaN(ponderer$storedBaseScale)) {
            scene.ponderer$setScaleFactor(ponderer$storedBaseScale);
            ponderer$storedBaseScale = Float.NaN;
        }
    }

    private void ponderer$applyViewOffsetCommon(PoseStack ms, float pt) {
        PonderSceneViewOffsetAccess access = (PonderSceneViewOffsetAccess) this$0;
        float ox = access.ponderer$getViewOffsetX().getValue(pt);
        float oy = access.ponderer$getViewOffsetY().getValue(pt);
        float oz = access.ponderer$getViewOffsetZ().getValue(pt);
        if (ox == 0 && oy == 0 && oz == 0) {
            return;
        }
        ms.translate(ox, oy, oz);
    }
}
