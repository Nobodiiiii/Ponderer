package com.nododiiiii.ponderer.mixin;

import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.20.1 has no public height setter; keep the source UI's exact input bounds. */
@Mixin(AbstractWidget.class)
public interface AbstractWidgetSizeAccessor {
    @Accessor("height") void ponderer$setHeight(int height);
}
