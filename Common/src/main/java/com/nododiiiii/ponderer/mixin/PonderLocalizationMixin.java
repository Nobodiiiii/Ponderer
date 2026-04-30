package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.Ponderer;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * Ensures Ponderer's own shared texts and scene-specific texts are readable
 * from the in-memory maps without enabling global editing mode.
 *
 * Ponderer scenes are registered dynamically at runtime and have no lang file
 * entries. Without editing mode, PonderLocalization.getShared/getSpecific would
 * call I18n.get() which returns raw lang keys for ponderer entries.
 *
 * This mixin intercepts those calls for the "ponderer" namespace only, returning
 * from the in-memory map. All other namespaces (e.g. "create") continue to use
 * I18n.get() for proper localization.
 */
@Mixin(PonderLocalization.class)
public class PonderLocalizationMixin {

            @Shadow(remap = false)
    public Map<ResourceLocation, String> shared;

            @Shadow(remap = false)
    public Map<ResourceLocation, Map<String, String>> specific;

    /**
     * Fires for both getShared(RL) and getShared(RL, Object...).
     * Only captures the first parameter so it is compatible with all overloads.
     * The formatted overload will receive the raw value (formatting is handled
     * by the caller if needed); this is acceptable for ponderer-namespace keys
     * because dynamic scene text rarely contains format specifiers.
     */
    @Inject(method = "getShared",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void ponderer$getShared(ResourceLocation key, CallbackInfoReturnable<String> cir) {
        if (PonderIndex.editingModeActive()) return;
        if (!Ponderer.MODID.equals(key.getNamespace())) return;
        String val = shared.get(key);
        if (val != null) {
            cir.setReturnValue(val);
        }
    }

    /**
     * Fires for both getSpecific(RL, String) and getSpecific(RL, String, Object...).
     * Same rationale as above – only common parameters are captured.
     */
    @Inject(method = "getSpecific",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void ponderer$getSpecific(ResourceLocation sceneId, String k, CallbackInfoReturnable<String> cir) {
        if (PonderIndex.editingModeActive()) return;
        if (!Ponderer.MODID.equals(sceneId.getNamespace())) return;
        Map<String, String> map = specific.get(sceneId);
        if (map != null) {
            String val = map.get(k);
            if (val != null) {
                cir.setReturnValue(val);
            }
        }
    }
}
