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

    @Shadow
    public Map<ResourceLocation, String> shared;

    @Shadow
    public Map<ResourceLocation, Map<String, String>> specific;

    @Inject(method = "getShared(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void ponderer$getShared(ResourceLocation key, CallbackInfoReturnable<String> cir) {
        if (PonderIndex.editingModeActive()) return;
        if (!Ponderer.MODID.equals(key.getNamespace())) return;
        String val = shared.get(key);
        if (val != null) {
            cir.setReturnValue(val);
        }
    }

    @Inject(method = "getShared(Lnet/minecraft/resources/ResourceLocation;[Ljava/lang/Object;)Ljava/lang/String;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void ponderer$getSharedFormatted(ResourceLocation key, Object[] params, CallbackInfoReturnable<String> cir) {
        if (PonderIndex.editingModeActive()) return;
        if (!Ponderer.MODID.equals(key.getNamespace())) return;
        String val = shared.get(key);
        if (val != null) {
            cir.setReturnValue(String.format(val, params));
        }
    }

    @Inject(method = "getSpecific(Lnet/minecraft/resources/ResourceLocation;Ljava/lang/String;)Ljava/lang/String;",
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

    @Inject(method = "getSpecific(Lnet/minecraft/resources/ResourceLocation;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void ponderer$getSpecificFormatted(ResourceLocation sceneId, String k, Object[] params, CallbackInfoReturnable<String> cir) {
        if (PonderIndex.editingModeActive()) return;
        if (!Ponderer.MODID.equals(sceneId.getNamespace())) return;
        Map<String, String> map = specific.get(sceneId);
        if (map != null) {
            String val = map.get(k);
            if (val != null) {
                cir.setReturnValue(String.format(val, params));
            }
        }
    }
}
