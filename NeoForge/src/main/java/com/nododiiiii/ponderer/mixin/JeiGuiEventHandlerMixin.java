package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.client.ClientInputHandler;
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
 *
 * <p>JEI 19.x renamed the GUI event handler entry points at some point:
 * <ul>
 *   <li>legacy: {@code onDrawBackgroundPost(Screen, GuiGraphics)},
 *       {@code onDrawForeground(AbstractContainerScreen, GuiGraphics, int, int)},
 *       {@code onDrawScreenPost(Screen, GuiGraphics, int, int)}</li>
 *   <li>current: {@code drawForScreenBackground(Screen, GuiGraphics)},
 *       {@code drawForScreenForeground(Screen, GuiGraphics, int, int)}</li>
 * </ul>
 * Every injector is {@code require = 0} and they all share one {@code @Group}, so exactly the
 * injectors that exist on the running JEI version apply and the mixin never hard-fails the game.
 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.events.GuiEventHandler")
public abstract class JeiGuiEventHandlerMixin {

    // ---------------------------------------------------------------------
    // Current JEI (19.6x+): drawForScreenBackground / drawForScreenForeground
    // ---------------------------------------------------------------------

    @Group(name = "ponderer$skipEmbeddedMirrorJeiDraw", min = 1)
    @Inject(method = "drawForScreenBackground", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ponderer$skipJeiBackgroundDraw(Screen screen, GuiGraphics guiGraphics, CallbackInfo ci) {
        if (ponderer$shouldSkipDefaultJeiDraw(screen)) {
            ci.cancel();
        }
    }

    @Group(name = "ponderer$skipEmbeddedMirrorJeiDraw", min = 1)
    @Inject(method = "drawForScreenForeground", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ponderer$skipJeiForegroundDraw(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY,
            CallbackInfo ci) {
        if (ponderer$shouldSkipDefaultJeiDraw(screen)) {
            ci.cancel();
        }
    }

    // ---------------------------------------------------------------------
    // Legacy JEI: onDrawBackgroundPost / onDrawForeground / onDrawScreenPost
    // ---------------------------------------------------------------------

    @Group(name = "ponderer$skipEmbeddedMirrorJeiDraw", min = 1)
    @Inject(method = "onDrawBackgroundPost", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ponderer$skipLegacyJeiBackgroundDraw(Screen screen, GuiGraphics guiGraphics, CallbackInfo ci) {
        if (ponderer$shouldSkipDefaultJeiDraw(screen)) {
            ci.cancel();
        }
    }

    @Group(name = "ponderer$skipEmbeddedMirrorJeiDraw", min = 1)
    @Inject(method = "onDrawForeground", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ponderer$skipLegacyJeiForegroundDraw(AbstractContainerScreen<?> screen, GuiGraphics guiGraphics,
            int mouseX, int mouseY, CallbackInfo ci) {
        if (ponderer$shouldSkipDefaultJeiDraw(screen)) {
            ci.cancel();
        }
    }

    @Group(name = "ponderer$skipEmbeddedMirrorJeiDraw", min = 1)
    @Inject(method = "onDrawScreenPost", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ponderer$skipLegacyJeiScreenPostDraw(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY,
            CallbackInfo ci) {
        if (ponderer$shouldSkipDefaultJeiDraw(screen)) {
            ci.cancel();
        }
    }

    private static boolean ponderer$shouldSkipDefaultJeiDraw(Screen screen) {
        return ClientInputHandler.isRenderingEmbeddedMirror()
            || JeiCompat.shouldRenderPonderUiOverlayManually(screen);
    }
}
