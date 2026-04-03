package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.forge.sticksnapshot.client.ClientInputHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class EmbeddedMirrorContainerScreenMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void ponderer$raiseEmbeddedContainer(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        Screen mirror = ClientInputHandler.getEmbeddedMirrorScreen();
        if (!ClientInputHandler.isRenderingEmbeddedMirror() || mirror == null || mirror != (Object) this) {
            return;
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void ponderer$restoreEmbeddedContainerZ(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
    }
}
