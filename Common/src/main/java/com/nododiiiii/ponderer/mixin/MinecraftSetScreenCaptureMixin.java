package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.ui.ClientScreenCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts {@link Minecraft#setScreen} only while the held-item {@code show_interface}
 * flow has armed {@link ClientScreenCapture}. The captured Screen is then embedded in
 * PonderUI as a mirrored child rather than replacing it.
 */
@Mixin(Minecraft.class)
public class MinecraftSetScreenCaptureMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true, require = 0)
    private void ponderer$captureSetScreen(Screen screen, CallbackInfo ci) {
        if (!ClientScreenCapture.isCapturing()) {
            return;
        }
        if (screen == null) {
            // Item.use may try to close the current screen first; ignore.
            ci.cancel();
            return;
        }
        ClientScreenCapture.offer(screen);
        ci.cancel();
    }
}
