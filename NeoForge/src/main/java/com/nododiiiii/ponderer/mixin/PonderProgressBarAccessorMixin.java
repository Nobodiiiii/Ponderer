package com.nododiiiii.ponderer.mixin;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.ponder.foundation.ui.PonderProgressBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PonderProgressBar.class)
public interface PonderProgressBarAccessorMixin {

    @Accessor(value = "progress", remap = false)
    LerpedFloat ponderer$getProgress();
}
