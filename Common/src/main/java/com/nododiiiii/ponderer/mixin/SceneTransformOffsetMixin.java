package com.nododiiiii.ponderer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nododiiiii.ponderer.ponder.PonderSceneViewOffsetAccess;
import net.createmod.ponder.foundation.PonderScene;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PonderScene.SceneTransform.class)
public class SceneTransformOffsetMixin {

    @Shadow(remap = false)
    @Final
    private PonderScene this$0;

    @Unique
    private float ponderer$capturedPt;

    @Inject(method = "apply(Lcom/mojang/blaze3d/vertex/PoseStack;F)Lcom/mojang/blaze3d/vertex/PoseStack;", at = @At("HEAD"), remap = false)
    private void ponderer$capturePt(PoseStack ms, float pt, CallbackInfoReturnable<PoseStack> cir) {
        this.ponderer$capturedPt = pt;
    }

    @Redirect(method = "apply(Lcom/mojang/blaze3d/vertex/PoseStack;F)Lcom/mojang/blaze3d/vertex/PoseStack;",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", remap = true),
            require = 0, remap = false)
    private void ponderer$redirectScale(PoseStack ms, float sx, float sy, float sz) {
        PonderSceneViewOffsetAccess access = (PonderSceneViewOffsetAccess) this$0;
        if (access.ponderer$isScaleOverrideActive()) {
            float s = 30f * access.ponderer$getScaleOverride().getValue(ponderer$capturedPt);
            ms.scale(s, s, s);
        } else {
            ms.scale(sx, sy, sz);
        }
    }

    @Inject(method = "apply(Lcom/mojang/blaze3d/vertex/PoseStack;F)Lcom/mojang/blaze3d/vertex/PoseStack;", at = @At("TAIL"), remap = false)
    private void ponderer$applyViewOffset(PoseStack ms, float pt, CallbackInfoReturnable<PoseStack> cir) {
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
