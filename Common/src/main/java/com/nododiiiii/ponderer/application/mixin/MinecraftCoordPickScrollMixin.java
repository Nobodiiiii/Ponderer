package com.nododiiiii.ponderer.application.mixin;

import com.nododiiiii.ponderer.features.blueprint.BlueprintHandler;
import com.nododiiiii.ponderer.editor.capture.CoordPickState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts mouse wheel while coordinate pick mode is active.
 */
@Mixin(MouseHandler.class)
public class MinecraftCoordPickScrollMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void ponderer$interceptCoordPickScroll(long windowPointer, double xOffset, double yOffset, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        if (BlueprintHandler.INSTANCE != null && BlueprintHandler.INSTANCE.mouseScrolled(yOffset)) {
            ci.cancel();
            return;
        }
        if (CoordPickState.isActive() && CoordPickState.handleMouseScrolled(yOffset)) {
            ci.cancel();
        }
    }
}
