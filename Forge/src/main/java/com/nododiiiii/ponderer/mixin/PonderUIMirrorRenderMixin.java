package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.forge.sticksnapshot.client.ClientInputHandler;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PonderUI.class)
public abstract class PonderUIMirrorRenderMixin {

    @Inject(method = "renderScene", at = @At("HEAD"), cancellable = true, remap = false)
    private void ponderer$renderMirrorInsteadOfStructure(GuiGraphics graphics, int mouseX, int mouseY, int i,
            float partialTicks, CallbackInfo ci) {
        Screen mirror = ClientInputHandler.getEmbeddedMirrorScreen();
        if (mirror == null) {
            return;
        }
        mirror.render(graphics, mouseX, mouseY, partialTicks);
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void ponderer$tickEmbeddedMirror(CallbackInfo ci) {
        Screen mirror = ClientInputHandler.getEmbeddedMirrorScreen();
        if (mirror != null) {
            mirror.tick();
        }
    }

    @Inject(method = "replay", at = @At("HEAD"), remap = false)
    private void ponderer$closeEmbeddedOnReplay(CallbackInfo ci) {
        if (ClientInputHandler.hasEmbeddedMirrorScreen()) {
            ClientInputHandler.closeEmbeddedMirrorFromPonder("ponder-replay");
        }
    }

    @Inject(method = "scroll", at = @At("RETURN"), remap = false)
    private void ponderer$closeEmbeddedOnSceneSwitch(boolean forward, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && ClientInputHandler.hasEmbeddedMirrorScreen()) {
            ClientInputHandler.closeEmbeddedMirrorFromPonder("ponder-scene-switch");
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void ponderer$closeEmbeddedWhenPonderRemoved(CallbackInfo ci) {
        if (ClientInputHandler.hasEmbeddedMirrorScreen()) {
            ClientInputHandler.closeEmbeddedMirrorFromPonder("ponder-removed");
        }
    }
}
