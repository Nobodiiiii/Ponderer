package com.nododiiiii.ponderer.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Gui.class)
public interface GuiFeedbackAccessor {
    @Accessor("overlayMessageString") Component ponderer$getOverlayMessage();
    @Accessor("overlayMessageTime") void ponderer$setOverlayMessageTime(int ticks);
}
