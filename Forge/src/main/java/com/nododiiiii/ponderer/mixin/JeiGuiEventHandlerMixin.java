package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.forge.sticksnapshot.client.ClientInputHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppress JEI's default draw pass while an embedded mirror screen is being rendered
 * inside PonderUI. The host PonderUI will receive its own normal JEI pass later.
 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.events.GuiEventHandler")
public abstract class JeiGuiEventHandlerMixin {

    @Group(name = "ponderer$skipEmbeddedMirrorJeiDraw", min = 1, max = 3)
    @Inject(method = "onDrawBackgroundPost", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ponderer$skipJeiBackgroundDraw(Screen screen, GuiGraphics guiGraphics, CallbackInfo ci) {
        if (ponderer$shouldSkipDefaultJeiDraw(screen)) {
            ci.cancel();
        }
    }

    @Group(name = "ponderer$skipEmbeddedMirrorJeiDraw", min = 1, max = 3)
    @Inject(method = "onDrawForeground", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ponderer$skipJeiForegroundDraw(AbstractContainerScreen<?> screen, GuiGraphics guiGraphics,
            int mouseX, int mouseY, CallbackInfo ci) {
        if (ponderer$shouldSkipDefaultJeiDraw(screen)) {
            ci.cancel();
        }
    }

    @Group(name = "ponderer$skipEmbeddedMirrorJeiDraw", min = 1, max = 3)
    @Inject(method = "onDrawScreenPost", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ponderer$skipJeiScreenDraw(Screen screen, GuiGraphics guiGraphics,
            int mouseX, int mouseY, CallbackInfo ci) {
        if (ponderer$shouldSkipDefaultJeiDraw(screen)) {
            ci.cancel();
        }
    }

    private static boolean ponderer$shouldSkipDefaultJeiDraw(Screen screen) {
        return ClientInputHandler.isRenderingEmbeddedMirror()
            || JeiCompat.shouldRenderPonderUiOverlayManually(screen);
    }
}
