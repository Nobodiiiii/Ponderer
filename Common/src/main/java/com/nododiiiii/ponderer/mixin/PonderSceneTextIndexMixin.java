package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.ponder.TextIndexStore;
import com.nododiiiii.ponderer.projector.client.ProjectorCueIndexStore;
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
        ProjectorCueIndexStore.clear(scene);
    }
}
