package com.nododiiiii.ponderer.mixin;

import net.createmod.ponder.foundation.PonderScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PonderScene.class)
public interface PonderSceneAccessor {

    @Accessor(value = "scaleFactor", remap = false)
    float ponderer$getScaleFactor();

    @Accessor(value = "scaleFactor", remap = false)
    void ponderer$setScaleFactor(float value);

    @Accessor(value = "basePlateOffsetX", remap = false)
    int ponderer$getBasePlateOffsetX();

    @Accessor(value = "basePlateOffsetX", remap = false)
    void ponderer$setBasePlateOffsetX(int value);

    @Accessor(value = "basePlateOffsetZ", remap = false)
    int ponderer$getBasePlateOffsetZ();

    @Accessor(value = "basePlateOffsetZ", remap = false)
    void ponderer$setBasePlateOffsetZ(int value);

    @Accessor(value = "basePlateSize", remap = false)
    int ponderer$getBasePlateSize();
}
