package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.ponder.PonderRenderCache;
import com.nododiiiii.ponderer.ponder.PonderInterpolationSnapshot;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.element.WorldSectionElementImpl;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
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
public class WorldSectionElementImplMixin implements PonderInterpolationSnapshot.SectionAccess {

    @Shadow(remap = false)
    @Nullable
    Selection section;

    @Shadow
    Vec3 prevAnimatedOffset;

    @Shadow
    Vec3 prevAnimatedRotation;

    @Override
    public Vec3 ponderer$getPreviousOffset() {
        return prevAnimatedOffset;
    }

    @Override
    public void ponderer$setPreviousOffset(Vec3 offset) {
        prevAnimatedOffset = offset;
    }

    @Override
    public Vec3 ponderer$getPreviousRotation() {
        return prevAnimatedRotation;
    }

    @Override
    public void ponderer$setPreviousRotation(Vec3 rotation) {
        prevAnimatedRotation = rotation;
    }

    @Inject(method = "setAnimatedOffset", at = @At("RETURN"))
    private void ponderer$resetBatchOffset(Vec3 offset, boolean force, CallbackInfo ci) {
        if (force) {
            PonderInterpolationSnapshot.resetSectionOffset((WorldSectionElementImpl) (Object) this, offset);
        }
    }

    @Inject(method = "setAnimatedRotation", at = @At("RETURN"))
    private void ponderer$resetBatchRotation(Vec3 rotation, boolean force, CallbackInfo ci) {
        if (force) {
            PonderInterpolationSnapshot.resetSectionRotation((WorldSectionElementImpl) (Object) this, rotation);
        }
    }

    @Inject(method = "resetAnimatedTransform", at = @At("RETURN"), remap = false)
    private void ponderer$resetBatchTransform(CallbackInfo ci) {
        WorldSectionElementImpl self = (WorldSectionElementImpl) (Object) this;
        PonderInterpolationSnapshot.resetSectionOffset(self, prevAnimatedOffset);
        PonderInterpolationSnapshot.resetSectionRotation(self, prevAnimatedRotation);
    }

    @Inject(method = "renderLayer", at = @At("HEAD"))
    private void ponderer$trackRenderCache(PonderLevel world, MultiBufferSource buffer, RenderType layer,
                                         GuiGraphics graphics, float fade, float partialTicks, CallbackInfo ci) {
        PonderRenderCache.record(world, (WorldSectionElementImpl) (Object) this, layer);
    }

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
