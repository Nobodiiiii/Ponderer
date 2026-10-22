package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.interfaces.sandbox.SandboxScreenGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Interface sandbox screen gate: inside a sandbox scope the host PonderUI can never be
 * replaced or closed; screens opened by sandbox code go to the session instead.
 * Optional ({@code require = 0}); without it sessions stay read-only.
 */
@Mixin(Minecraft.class)
public class SandboxScreenGateMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true, require = 0)
    private void ponderer$sandboxScreenGate(Screen screen, CallbackInfo ci) {
        SandboxScreenGate.markLive();
        if (SandboxScreenGate.intercept(screen)) {
            ci.cancel();
        }
    }
}
