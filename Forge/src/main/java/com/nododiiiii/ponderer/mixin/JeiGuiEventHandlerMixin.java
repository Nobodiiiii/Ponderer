package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.ui.InterfaceSlotEditState;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.gui.events.GuiEventHandler")
public abstract class JeiGuiEventHandlerMixin {

    @Group(name = "ponderer$skipPonderUiJeiDraw", min = 1, max = 3)
    @Inject(method = "drawForScreen", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ponderer$skipLegacyJeiDraw(Screen screen, GuiGraphics guiGraphics,
                                            int mouseX, int mouseY, CallbackInfo ci) {
        if (ponderer$shouldSkipDefaultJeiDraw(screen)) {
            ci.cancel();
        }
    }

    @Group(name = "ponderer$skipPonderUiJeiDraw", min = 1, max = 3)
    @Inject(method = "onDrawBackgroundPost", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ponderer$skipJeiBackgroundDraw(Screen screen, GuiGraphics guiGraphics, CallbackInfo ci) {
        if (ponderer$shouldSkipDefaultJeiDraw(screen)) {
            ci.cancel();
        }
    }

    @Group(name = "ponderer$skipPonderUiJeiDraw", min = 1, max = 3)
    @Inject(method = "onDrawScreenPost", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ponderer$skipJeiScreenDraw(Screen screen, GuiGraphics guiGraphics,
                                            int mouseX, int mouseY, CallbackInfo ci) {
        if (ponderer$shouldSkipDefaultJeiDraw(screen)) {
            ci.cancel();
        }
    }

    private static boolean ponderer$shouldSkipDefaultJeiDraw(Screen screen) {
        return screen instanceof PonderUI && InterfaceSlotEditState.hasJeiViewport();
    }
}
