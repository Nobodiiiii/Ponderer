package com.nododiiiii.ponderer.mixin;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.foundation.element.TextWindowElement;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

@Mixin(TextWindowElement.class)
public interface TextWindowElementAccessor {

    @Accessor(value = "textGetter", remap = false)
    Supplier<String> ponderer$getTextGetter();

    @Accessor(value = "bakedText", remap = false)
    @Nullable
    String ponderer$getBakedText();

    @Accessor(value = "y", remap = false)
    int ponderer$getY();

    @Accessor(value = "vec", remap = false)
    @Nullable
    Vec3 ponderer$getVec();

    @Accessor(value = "palette", remap = false)
    PonderPalette ponderer$getPalette();
}
