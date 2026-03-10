package com.nododiiiii.ponderer.ponder;

import net.createmod.catnip.animation.LerpedFloat;

public interface PonderSceneViewOffsetAccess {
    LerpedFloat ponderer$getViewOffsetX();
    LerpedFloat ponderer$getViewOffsetY();
    LerpedFloat ponderer$getViewOffsetZ();
    LerpedFloat ponderer$getScaleOverride();
    boolean ponderer$isScaleOverrideActive();
    void ponderer$setScaleOverrideActive(boolean active);
    void ponderer$resetViewOffset();
    float ponderer$getDefaultScale();
    void ponderer$setDefaultScale(float value);
}
