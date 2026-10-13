package com.nododiiiii.ponderer.mixin;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.ponder.foundation.element.AnimatedOverlayElementBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AnimatedOverlayElementBase.class)
public interface AnimatedOverlayElementAccessor {
    @Accessor(value = "fade", remap = false)
    LerpedFloat ponderer$getFade();
}
