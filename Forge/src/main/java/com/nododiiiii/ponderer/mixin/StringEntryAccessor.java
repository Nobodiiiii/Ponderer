package com.nododiiiii.ponderer.mixin;

import net.createmod.catnip.config.ui.entries.StringEntry;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StringEntry.class)
public interface StringEntryAccessor {

    @Accessor("textField")
    EditBox ponderer$getTextField();
}