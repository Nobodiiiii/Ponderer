package com.nododiiiii.ponderer.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int ponderer$getLeftPos();

    @Accessor("topPos")
    int ponderer$getTopPos();

    @Accessor("imageWidth")
    int ponderer$getImageWidth();

    @Accessor("imageHeight")
    int ponderer$getImageHeight();
}
