package com.nododiiiii.ponderer.mixin;

import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.element.WorldSectionElementImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/**
 * Prevents NullPointerException when erase() or add() is called on a
 * WorldSectionElement whose internal Selection has not been initialized
 * (e.g. hide_section executed before any show_structure).
 */
@Mixin(WorldSectionElementImpl.class)
public class WorldSectionElementImplMixin {

    @Shadow(remap = false)
    @Nullable
    Selection section;

    @Inject(method = "erase", at = @At("HEAD"), cancellable = true, remap = false)
    private void ponderer$safeErase(Selection toErase, CallbackInfo ci) {
        if (section == null) {
            ci.cancel();
        }
    }

    @Inject(method = "add", at = @At("HEAD"), cancellable = true, remap = false)
    private void ponderer$safeAdd(Selection toAdd, CallbackInfo ci) {
        if (section == null) {
            ci.cancel();
        }
    }
}
