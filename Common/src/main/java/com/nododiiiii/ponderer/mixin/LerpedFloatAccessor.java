package com.nododiiiii.ponderer.mixin;

import net.createmod.catnip.animation.LerpedFloat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LerpedFloat.class)
public interface LerpedFloatAccessor {
    @Accessor(value = "value", remap = false)
    float ponderer$getValue();

    @Accessor(value = "previousValue", remap = false)
    float ponderer$getPreviousValue();

    @Accessor(value = "previousValue", remap = false)
    void ponderer$setPreviousValue(float value);
}
