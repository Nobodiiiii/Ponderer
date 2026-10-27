package com.nododiiiii.ponderer.runtime.mixin;

import net.createmod.ponder.foundation.instruction.TickingInstruction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TickingInstruction.class)
public interface TickingInstructionAccessor {

    @Accessor(value = "totalTicks", remap = false)
    int ponderer$getTotalTicks();
}
