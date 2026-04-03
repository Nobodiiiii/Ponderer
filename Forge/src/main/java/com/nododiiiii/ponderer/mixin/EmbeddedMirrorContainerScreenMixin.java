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
    @Unique
    private static final int PONDERER_EMBEDDED_CONTAINER_Z_OFFSET = 400;

    @Unique
    private boolean ponderer$zPushed;

    @Inject(method = "render", at = @At("HEAD"))
    private void ponderer$raiseEmbeddedContainer(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        Screen mirror = ClientInputHandler.getEmbeddedMirrorScreen();
        if (!ClientInputHandler.isRenderingEmbeddedMirror() || mirror == null || mirror != (Object) this) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, PONDERER_EMBEDDED_CONTAINER_Z_OFFSET);
        ponderer$zPushed = true;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void ponderer$restoreEmbeddedContainerZ(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        if (!ponderer$zPushed) {
            return;
        }
        ponderer$zPushed = false;
        graphics.pose().popPose();
    }
}
