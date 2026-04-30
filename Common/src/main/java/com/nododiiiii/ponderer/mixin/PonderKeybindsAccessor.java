package com.nododiiiii.ponderer.mixin;

import net.createmod.ponder.enums.PonderKeybinds;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PonderKeybinds.class)
public interface PonderKeybindsAccessor {

    @Accessor(value = "mapping", remap = false)
    KeyMapping ponderer$getMapping();
}
