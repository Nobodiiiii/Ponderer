package com.nododiiiii.ponderer.runtime.mixin;

import com.nododiiiii.ponderer.runtime.api.PonderSceneViewOffsetAccess;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.ponder.foundation.PonderScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PonderScene.class)
public class PonderSceneViewOffsetMixin implements PonderSceneViewOffsetAccess {

    @Unique
    private LerpedFloat ponderer$viewOffsetX;

    @Unique
    private LerpedFloat ponderer$viewOffsetY;

    @Unique
    private LerpedFloat ponderer$viewOffsetZ;

    @Unique
    private LerpedFloat ponderer$scaleOverride;

    @Override public boolean ponderer$hasViewState() { return ponderer$viewOffsetX != null; }
    @Unique private void ponderer$initializeView() {
        if (ponderer$hasViewState()) return;
        ponderer$viewOffsetX = LerpedFloat.linear().startWithValue(0);
        ponderer$viewOffsetY = LerpedFloat.linear().startWithValue(0);
        ponderer$viewOffsetZ = LerpedFloat.linear().startWithValue(0);
        ponderer$scaleOverride = LerpedFloat.linear().startWithValue(1);
    }

    @Unique
    private boolean ponderer$scaleOverrideActive = false;

    @Unique
    private float ponderer$defaultScale = Float.NaN;

    @Override
    public LerpedFloat ponderer$getViewOffsetX() {
        ponderer$initializeView();
        return ponderer$viewOffsetX;
    }

    @Override
    public LerpedFloat ponderer$getViewOffsetY() {
        ponderer$initializeView();
        return ponderer$viewOffsetY;
    }

    @Override
    public LerpedFloat ponderer$getViewOffsetZ() {
        ponderer$initializeView();
        return ponderer$viewOffsetZ;
    }

    @Override
    public LerpedFloat ponderer$getScaleOverride() {
        ponderer$initializeView();
        return ponderer$scaleOverride;
    }

    @Override
    public boolean ponderer$isScaleOverrideActive() {
        return ponderer$scaleOverrideActive;
    }

    @Override
    public void ponderer$setScaleOverrideActive(boolean active) {
        if (active) ponderer$initializeView();
        this.ponderer$scaleOverrideActive = active;
    }

    @Override
    public void ponderer$resetViewOffset() {
        if (!ponderer$hasViewState()) return;
        ponderer$viewOffsetX.startWithValue(0);
        ponderer$viewOffsetY.startWithValue(0);
        ponderer$viewOffsetZ.startWithValue(0);
        ponderer$scaleOverride.startWithValue(1);
        ponderer$scaleOverrideActive = false;
    }

    @Override
    public float ponderer$getDefaultScale() {
        return ponderer$defaultScale;
    }

    @Override
    public void ponderer$setDefaultScale(float value) {
        this.ponderer$defaultScale = value;
    }

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void ponderer$tickViewOffset(CallbackInfo ci) {
        if (!ponderer$hasViewState()) return;
        ponderer$viewOffsetX.tickChaser();
        ponderer$viewOffsetY.tickChaser();
        ponderer$viewOffsetZ.tickChaser();
        ponderer$scaleOverride.tickChaser();
    }
}
