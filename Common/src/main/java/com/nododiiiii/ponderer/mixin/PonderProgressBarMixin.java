package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.ui.PonderSmallHint;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.widget.AbstractSimiWidget;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderProgressBar;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PonderProgressBar.class)
public abstract class PonderProgressBarMixin extends AbstractSimiWidget {

    @Unique
    private static final double ponderer$SNAP_DISTANCE = 6;

    @Shadow(remap = false)
    private PonderUI ponder;

    @Shadow(remap = false)
    private LerpedFloat progress;

    protected PonderProgressBarMixin(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    @Inject(method = "doRender", at = @At("TAIL"))
    private void ponderer$renderDragHint(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks,
            CallbackInfo ci) {
        if (active && visible && isHovered()) {
            PonderSmallHint.render(graphics, Component.translatable("ponderer.ui.playback.drag_hint"),
                getX() + getWidth() / 2, getY() + getHeight() + 4, ponder.height - 16,
                UIRenderHelper.COLOR_TEXT_DARKER.getFirst().getRGB());
        }
    }

    @Inject(method = "clicked(DD)Z", at = @At("HEAD"), cancellable = true)
    private void ponderer$allowClickWithoutKeyframes(double mouseX, double mouseY,
            CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(active && visible && ponder.getActiveScene().getTotalTime() > 0
            && mouseX >= getX() - 2 && mouseX < getX() + getWidth() + 4
            && mouseY >= getY() - 3 && mouseY < getY() + getHeight() + 20);
    }

    @Inject(method = "setFocused(Z)V", at = @At("HEAD"), cancellable = true)
    private void ponderer$retainFocusWhileScrubbing(boolean focused, CallbackInfo ci) {
        // AbstractSimiScreen drops widgets that refuse focus before dispatching drag events.
        super.setFocused(focused);
        ci.cancel();
    }

    @Inject(method = "onClick(DD)V", at = @At("HEAD"), cancellable = true)
    private void ponderer$seekToClickedProgress(double mouseX, double mouseY, CallbackInfo ci) {
        ponderer$seekToMouse(mouseX);
        ci.cancel();
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        ponderer$seekToMouse(mouseX);
    }

    @Unique
    private void ponderer$seekToMouse(double mouseX) {
        PonderScene scene = ponder.getActiveScene();
        int totalTime = scene.getTotalTime();
        if (totalTime > 0) {
            // Match the rendered fill, which starts two pixels before the widget bounds.
            double progress = (mouseX - (getX() - 2)) / (getWidth() + 4.0);
            int targetTime = (int) Math.round(Mth.clamp(progress, 0, 1) * totalTime);
            int keyframeIndex = ponderer$findNearbyKeyframe(scene, mouseX);
            if (keyframeIndex == -1) {
                targetTime = 0;
            } else if (keyframeIndex == scene.getKeyframeCount()) {
                targetTime = totalTime;
            } else if (keyframeIndex >= 0) {
                targetTime = scene.getKeyframeTime(keyframeIndex);
            }
            if (targetTime != scene.getCurrentTime()) {
                ponder.seekToTime(targetTime);
            }
            this.progress.startWithValue(ponder.getActiveScene().getSceneProgress());
        }
    }

    @Inject(method = "getHoveredKeyframeIndex", at = @At("HEAD"), cancellable = true, remap = false)
    private void ponderer$highlightNearbyKeyframe(PonderScene scene, double mouseX,
            CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ponderer$findNearbyKeyframe(scene, mouseX));
    }

    @Unique
    private int ponderer$findNearbyKeyframe(PonderScene scene, double mouseX) {
        int totalTime = scene.getTotalTime();
        if (totalTime <= 0) {
            return -2;
        }

        // -2 means no highlight; -1 and keyframeCount are Ponder's start/end markers.
        int nearestIndex = -2;
        double nearestDistance = ponderer$SNAP_DISTANCE;
        double startDistance = Math.abs(mouseX - (getX() - 1));
        if (startDistance <= nearestDistance) {
            nearestIndex = -1;
            nearestDistance = startDistance;
        }
        double endDistance = Math.abs(mouseX - (getX() + getWidth() + 3));
        if (endDistance <= nearestDistance) {
            nearestIndex = scene.getKeyframeCount();
            nearestDistance = endDistance;
        }
        for (int i = 0; i < scene.getKeyframeCount(); i++) {
            int keyframePos = (int) ((float) scene.getKeyframeTime(i) / totalTime * (getWidth() + 2));
            double distance = Math.abs(mouseX - (getX() - 1 + keyframePos));
            if (distance <= nearestDistance) {
                nearestIndex = i;
                nearestDistance = distance;
            }
        }
        return nearestIndex;
    }
}
