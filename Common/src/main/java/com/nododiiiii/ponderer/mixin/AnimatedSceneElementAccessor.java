package com.nododiiiii.ponderer.mixin;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.ponder.foundation.element.AnimatedSceneElementBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AnimatedSceneElementBase.class)
public interface AnimatedSceneElementAccessor {
    @Accessor(value = "fade", remap = false)
    LerpedFloat ponderer$getFade();
}
