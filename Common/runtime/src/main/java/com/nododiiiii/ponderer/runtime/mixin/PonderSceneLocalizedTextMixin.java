package com.nododiiiii.ponderer.runtime.mixin;

import com.nododiiiii.ponderer.runtime.api.RuntimeScenes;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.function.Supplier;

/** Lib resolves each document's text before building; upstream language keys must not replace it. */
@Mixin(PonderScene.class)
public abstract class PonderSceneLocalizedTextMixin {
    @Shadow(remap = false) @Final private PonderLocalization localization;

    @Inject(method = "registerText(Ljava/lang/String;)Ljava/util/function/Supplier;", at = @At("RETURN"), cancellable = true, remap = false)
    private void ponderer$literalText(String text, CallbackInfoReturnable<Supplier<String>> callback) {
        if (RuntimeScenes.owns(((PonderScene) (Object) this).getWorld())) callback.setReturnValue(() -> text);
    }

    @Inject(method = "registerText(Ljava/lang/String;[Ljava/lang/Object;)Ljava/util/function/Supplier;", at = @At("RETURN"), cancellable = true, remap = false)
    private void ponderer$formattedText(String text, Object[] parameters, CallbackInfoReturnable<Supplier<String>> callback) {
        if (RuntimeScenes.owns(((PonderScene) (Object) this).getWorld())) callback.setReturnValue(() -> String.format(text, parameters));
    }

    @Inject(method = "getString(Ljava/lang/String;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true, remap = false)
    private void ponderer$literalTitle(String key, CallbackInfoReturnable<String> callback) {
        var scene = (PonderScene) (Object) this;
        if (!RuntimeScenes.owns(scene.getWorld())) return;
        var values = localization.specific.get(scene.getId());
        if (values != null && values.containsKey(key)) callback.setReturnValue(values.get(key));
    }
}
