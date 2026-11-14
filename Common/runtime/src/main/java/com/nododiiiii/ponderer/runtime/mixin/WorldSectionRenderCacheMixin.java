package com.nododiiiii.ponderer.runtime.mixin;

import com.nododiiiii.ponderer.runtime.render.PonderRenderCache;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.element.WorldSectionElementImpl;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cache tracking has an independent capability from section safety. */
@Mixin(WorldSectionElementImpl.class)
public class WorldSectionRenderCacheMixin {
    @Inject(method = "renderLayer", at = @At("HEAD"))
    private void ponderer$trackRenderCache(PonderLevel world, MultiBufferSource buffer, RenderType layer,
                                         GuiGraphics graphics, float fade, float partialTicks, CallbackInfo ci) {
        PonderRenderCache.record(world, (WorldSectionElementImpl) (Object) this, layer);
    }
}
