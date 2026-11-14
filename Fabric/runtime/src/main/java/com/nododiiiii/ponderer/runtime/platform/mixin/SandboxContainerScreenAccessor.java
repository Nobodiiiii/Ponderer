package com.nododiiiii.ponderer.runtime.platform.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface SandboxContainerScreenAccessor {
    @Accessor("leftPos") int ponderer$left();
    @Accessor("topPos") int ponderer$top();
    @Accessor("imageWidth") int ponderer$width();
    @Accessor("imageHeight") int ponderer$height();
    @Accessor("hoveredSlot") Slot ponderer$hoveredSlot();
}
