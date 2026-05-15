package com.nododiiiii.ponderer.mixin;

import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MultilineTextField.class)
public interface MultilineTextFieldAccessor {

    @Accessor("selectCursor")
    int ponderer$getSelectCursor();

    @Accessor("selectCursor")
    void ponderer$setSelectCursor(int selectCursor);
}
