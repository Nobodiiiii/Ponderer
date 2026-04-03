package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.ui.InterfaceSlotEditState;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.gui.events.GuiEventHandler")
public abstract class JeiGuiEventHandlerMixin {

    @Inject(method = "drawForScreen", at = @At("HEAD"), cancellable = true, remap = false)
    private void ponderer$skipPonderUiJeiDraw(Screen screen, GuiGraphics guiGraphics,
                                              int mouseX, int mouseY, CallbackInfo ci) {
        if (screen instanceof PonderUI && InterfaceSlotEditState.hasJeiViewport()) {
            ci.cancel();
        }
    }
}
