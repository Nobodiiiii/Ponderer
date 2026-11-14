package com.nododiiiii.ponderer.runtime.mixin;

import com.nododiiiii.ponderer.runtime.core.TextIndexStore;
import com.nododiiiii.ponderer.runtime.api.OverlayTimeline;
import net.createmod.ponder.foundation.PonderScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PonderScene.class)
public class PonderSceneTextIndexMixin {

    @Inject(method = "begin", at = @At("HEAD"), remap = false)
    private void ponderer$clearTextIndex(CallbackInfo ci) {
        PonderScene scene = (PonderScene) (Object) this;
        TextIndexStore.clear(scene);
        OverlayTimeline.clear(scene);
    }
}
