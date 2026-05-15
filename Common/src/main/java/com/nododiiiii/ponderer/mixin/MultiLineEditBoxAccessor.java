package com.nododiiiii.ponderer.mixin;

import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiLineEditBox.class)
public interface MultiLineEditBoxAccessor {

    @Accessor("textField")
    MultilineTextField ponderer$getTextField();

    @Invoker("seekCursorScreen")
    void ponderer$seekCursorScreen(double mouseX, double mouseY);
}
