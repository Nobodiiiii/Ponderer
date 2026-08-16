package com.nododiiiii.ponderer.mixin;

import net.createmod.catnip.config.ui.entries.StringEntry;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StringEntry.class)
public class StringEntryMaxLengthMixin {

    private static final int PONDERER_MAX_LENGTH = 65536;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void ponderer$raiseMaxLength(String label, net.minecraftforge.common.ForgeConfigSpec.ConfigValue<String> value,
                                         net.minecraftforge.common.ForgeConfigSpec.ValueSpec spec, CallbackInfo ci) {
        EditBox textField = ((StringEntryAccessor) (Object) this).ponderer$getTextField();
        if (textField != null) {
            textField.setMaxLength(PONDERER_MAX_LENGTH);
            textField.setValue(value.get());
        }
    }
}