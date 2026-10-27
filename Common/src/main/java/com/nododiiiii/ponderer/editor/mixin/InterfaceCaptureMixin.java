package com.nododiiiii.ponderer.editor.mixin;

import com.nododiiiii.ponderer.editor.capture.InterfaceCapture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class InterfaceCaptureMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void ponderer$captureUse(CallbackInfo ci) { InterfaceCapture.beforeUse(); }
    @Inject(method = "tick", at = @At("TAIL"))
    private void ponderer$captureTick(CallbackInfo ci) { InterfaceCapture.tick(); }
}
