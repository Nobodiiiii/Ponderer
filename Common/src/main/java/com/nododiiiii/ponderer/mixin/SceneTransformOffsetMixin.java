package com.nododiiiii.ponderer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nododiiiii.ponderer.runtime.core.PonderSceneViewOffsetAccess;
import com.nododiiiii.ponderer.ui.PonderUiZoomAccess;
import com.nododiiiii.ponderer.ui.PonderUiPreviewAccess;
import com.nododiiiii.ponderer.ui.PonderFreePreview;
import com.nododiiiii.ponderer.runtime.viewer.UiAnchorPoint;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PonderScene.SceneTransform.class)
public class SceneTransformOffsetMixin {
    @Shadow(remap = false) public LerpedFloat xRotation;
    @Shadow(remap = false) public LerpedFloat yRotation;
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

    @Inject(method = "sceneToScreen", at = @At("HEAD"), cancellable = true)
    private void ponderer$resolveUiAnchor(Vec3 vec, float pt, CallbackInfoReturnable<Vec2> cir) {
        if (vec instanceof UiAnchorPoint anchor) {
            cir.setReturnValue(anchor.toScreen());
        }
    }

    @Unique
    private PonderFreePreview ponderer$preview() {
        if (Minecraft.getInstance().screen instanceof PonderUI ui) {
            PonderFreePreview preview = ((PonderUiPreviewAccess) ui).ponderer$getFreePreview();
            if (preview.appliesTo(ponderer$scene)) {
                return preview;
            }
        }
        return null;
    }

    @ModifyVariable(method = {
        "apply(Lcom/mojang/blaze3d/vertex/PoseStack;F)Lcom/mojang/blaze3d/vertex/PoseStack;", "screenToScene"
    }, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float ponderer$previewPartialTicks(float pt) {
        PonderFreePreview preview = ponderer$preview();
        return preview == null ? pt : preview.partialTicks();
    }

    @Redirect(method = {
        "apply(Lcom/mojang/blaze3d/vertex/PoseStack;F)Lcom/mojang/blaze3d/vertex/PoseStack;", "screenToScene"
    }, at = @At(value = "INVOKE", target = "Lnet/createmod/catnip/animation/LerpedFloat;getValue(F)F", remap = false))
    private float ponderer$previewRotation(LerpedFloat rotation, float pt) {
        PonderFreePreview preview = ponderer$preview();
        float value = rotation.getValue(pt);
        if (preview != null) {
            if (rotation == xRotation) value += preview.pitch();
            if (rotation == yRotation) value += preview.yaw();
        }
        return value;
    }

    @Inject(method = "apply(Lcom/mojang/blaze3d/vertex/PoseStack;F)Lcom/mojang/blaze3d/vertex/PoseStack;", at = @At("HEAD"))
    private void ponderer$previewPan(PoseStack ms, float pt, CallbackInfoReturnable<PoseStack> cir) {
        PonderFreePreview preview = ponderer$preview();
        if (preview != null) {
            ms.translate(preview.panX(), preview.panY(), 0);
        }
    }

    @ModifyVariable(method = "screenToScene", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double ponderer$unpanMouseX(double x) {
        PonderFreePreview preview = ponderer$preview();
        return preview == null ? x : x - preview.panX();
    }

    @ModifyVariable(method = "screenToScene", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double ponderer$unpanMouseY(double y) {
        PonderFreePreview preview = ponderer$preview();
        return preview == null ? y : y - preview.panY();
    }

    @Redirect(method = {
        "apply(Lcom/mojang/blaze3d/vertex/PoseStack;F)Lcom/mojang/blaze3d/vertex/PoseStack;",
        "screenToScene"
    }, at = @At(value = "FIELD", target = "Lnet/createmod/ponder/foundation/PonderScene;scaleFactor:F", remap = false))
    private float ponderer$effectiveScale(PonderScene scene) {
        PonderSceneViewOffsetAccess access = (PonderSceneViewOffsetAccess) scene;
        PonderFreePreview preview = ponderer$preview();
        float pt = preview == null ? ponderer$capturedPt : preview.partialTicks();
        float scale = access.ponderer$isScaleOverrideActive()
            ? access.ponderer$getScaleOverride().getValue(pt)
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

    @Inject(method = "screenToScene", at = @At("RETURN"), cancellable = true)
    private void ponderer$pickPreviewWithViewOffset(double x, double y, int depth, float pt,
            CallbackInfoReturnable<Vec3> cir) {
        PonderFreePreview preview = ponderer$preview();
        if (preview != null) {
            PonderSceneViewOffsetAccess access = (PonderSceneViewOffsetAccess) ponderer$scene;
            float frozenPt = preview.partialTicks();
            cir.setReturnValue(cir.getReturnValue().subtract(
                access.ponderer$getViewOffsetX().getValue(frozenPt),
                access.ponderer$getViewOffsetY().getValue(frozenPt),
                access.ponderer$getViewOffsetZ().getValue(frozenPt)));
        }
    }
}
