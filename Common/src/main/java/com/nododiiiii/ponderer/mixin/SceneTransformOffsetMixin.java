package com.nododiiiii.ponderer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nododiiiii.ponderer.ponder.PonderSceneViewOffsetAccess;
import com.nododiiiii.ponderer.ui.PonderUiZoomAccess;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PonderScene.SceneTransform.class)
public class SceneTransformOffsetMixin {
    @Unique private PonderScene ponderer$scene;
    @Unique private float ponderer$capturedPt;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void ponderer$captureScene(PonderScene scene, CallbackInfo ci) {
        ponderer$scene = scene;
    }

    @Inject(method = "apply(Lcom/mojang/blaze3d/vertex/PoseStack;F)Lcom/mojang/blaze3d/vertex/PoseStack;", at = @At("HEAD"))
    private void ponderer$capturePt(PoseStack ms, float pt, CallbackInfoReturnable<PoseStack> cir) {
        ponderer$capturedPt = pt;
    }

    @Inject(method = "screenToScene", at = @At("HEAD"))
    private void ponderer$capturePickingPt(double x, double y, int depth, float pt, CallbackInfoReturnable<Vec3> cir) {
        ponderer$capturedPt = pt;
    }

    @Redirect(method = {
        "apply(Lcom/mojang/blaze3d/vertex/PoseStack;F)Lcom/mojang/blaze3d/vertex/PoseStack;",
        "screenToScene"
    }, at = @At(value = "FIELD", target = "Lnet/createmod/ponder/foundation/PonderScene;scaleFactor:F", remap = false))
    private float ponderer$effectiveScale(PonderScene scene) {
        PonderSceneViewOffsetAccess access = (PonderSceneViewOffsetAccess) scene;
        float scale = access.ponderer$isScaleOverrideActive()
            ? access.ponderer$getScaleOverride().getValue(ponderer$capturedPt)
            : ((PonderSceneAccessor) scene).ponderer$getScaleFactor();
        // Keep the stored script scale untouched, including during seeking and replay.
        if (Minecraft.getInstance().screen instanceof PonderUI ui
            && ((PonderUIAccessor) ui).ponderer$getScenes().contains(scene)) {
            scale *= ((PonderUiZoomAccess) ui).ponderer$getUserZoom();
        }
        return scale;
    }

    @Inject(method = "apply(Lcom/mojang/blaze3d/vertex/PoseStack;F)Lcom/mojang/blaze3d/vertex/PoseStack;", at = @At("TAIL"))
    private void ponderer$applyViewOffset(PoseStack ms, float pt, CallbackInfoReturnable<PoseStack> cir) {
        PonderSceneViewOffsetAccess access = (PonderSceneViewOffsetAccess) ponderer$scene;
        float ox = access.ponderer$getViewOffsetX().getValue(pt);
        float oy = access.ponderer$getViewOffsetY().getValue(pt);
        float oz = access.ponderer$getViewOffsetZ().getValue(pt);
        if (ox != 0 || oy != 0 || oz != 0) {
            ms.translate(ox, oy, oz);
        }
    }
}
