package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.ponder.PonderInterpolationSnapshot;
import com.nododiiiii.ponderer.ui.PonderSpeedButton;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.createmod.ponder.foundation.ui.PonderProgressBar;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PonderUI.class)
public abstract class PonderPlaybackMixin extends Screen {
    @Shadow(remap = false) private PonderButton left;
    @Shadow(remap = false) private PonderButton right;
    @Shadow(remap = false) private PonderButton close;
    @Shadow(remap = false) private int extendedTickLength;
    @Shadow(remap = false) private int extendedTickTimer;
    @Shadow(remap = false) private int skipCooling;
    @Shadow(remap = false) private int finishingFlashWarmup;
    @Shadow(remap = false) private int nextUpWarmup;

    @Unique private double ponderer$playbackSpeed = 1;
    @Unique private PonderSpeedButton ponderer$halfSpeed;
    @Unique private PonderSpeedButton ponderer$normalSpeed;
    @Unique private PonderSpeedButton ponderer$doubleSpeed;
    @Unique private PonderScene ponderer$previousScene;
    @Unique private int ponderer$previousTime;
    @Unique private int ponderer$previousPonderTicks;

    protected PonderPlaybackMixin() {
        super(CommonComponents.EMPTY);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void ponderer$addSpeedButtons(CallbackInfo ci) {
        int buttonY = left.getY() - 18;
        ponderer$halfSpeed = addRenderableWidget(new PonderSpeedButton(left.getX(), buttonY, "0.5x",
            Component.translatable("ponderer.ui.playback.half"), () -> ponderer$selectSpeed(.5)));
        ponderer$normalSpeed = addRenderableWidget(new PonderSpeedButton(close.getX(), buttonY, "1x",
            Component.translatable("ponderer.ui.playback.normal"), () -> ponderer$selectSpeed(1)));
        ponderer$doubleSpeed = addRenderableWidget(new PonderSpeedButton(right.getX(), buttonY, "2x",
            Component.translatable("ponderer.ui.playback.double"), () -> ponderer$selectSpeed(2)));
        ponderer$updateSpeedButtons();
    }

    @Unique
    private void ponderer$selectSpeed(double speed) {
        if (ponderer$playbackSpeed == speed) {
            return;
        }
        ponderer$playbackSpeed = speed;
        extendedTickTimer = 0;
        ponderer$updateSpeedButtons();
    }

    @Unique
    private void ponderer$updateSpeedButtons() {
        ponderer$setSpeedButtonSelected(ponderer$halfSpeed, ponderer$playbackSpeed == .5);
        ponderer$setSpeedButtonSelected(ponderer$normalSpeed, ponderer$playbackSpeed == 1);
        ponderer$setSpeedButtonSelected(ponderer$doubleSpeed, ponderer$playbackSpeed == 2);
    }

    @Unique
    private void ponderer$setSpeedButtonSelected(PonderSpeedButton button, boolean selected) {
        button.withCustomTheme(selected ? PonderButton.COLOR_CLICK : PonderButton.COLOR_IDLE,
            selected ? PonderButton.COLOR_CLICK : PonderButton.COLOR_HOVER, null, null);
    }

    @Inject(method = "renderHoverTooltips", at = @At("TAIL"))
    private void ponderer$renderPlaybackHints(GuiGraphics graphics, int tooltipColor, CallbackInfo ci) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        // The navigation labels use this same footer row while hovered.
        if (!left.isHoveredOrFocused() && !close.isHoveredOrFocused() && !right.isHoveredOrFocused()) {
            graphics.drawCenteredString(Minecraft.getInstance().font,
                Component.translatable("ponderer.ui.playback.drag_hint"), width / 2, height - 16, tooltipColor);
        }
        for (PonderSpeedButton button : new PonderSpeedButton[]{ponderer$halfSpeed, ponderer$normalSpeed, ponderer$doubleSpeed}) {
            if (button != null && button.isVisible() && button.isHoveredOrFocused()) {
                graphics.drawCenteredString(Minecraft.getInstance().font, button.getMessage(),
                    button.getX() + button.getWidth() / 2, button.getY() - 14, tooltipColor);
            }
        }
        graphics.pose().popPose();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void ponderer$rememberPlaybackTime(CallbackInfo ci) {
        ponderer$previousScene = ((PonderUI) (Object) this).getActiveScene();
        ponderer$previousTime = ponderer$previousScene.getCurrentTime();
        ponderer$previousPonderTicks = PonderUI.ponderTicks;
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/createmod/ponder/foundation/ui/PonderUI;isComfyReadingEnabled()Z", remap = false))
    private boolean ponderer$applyPlaybackInterval(PonderUI ui) {
        // Reuse Ponder's extended-tick interpolation for smooth half-speed playback.
        extendedTickLength = ponderer$playbackSpeed == .5 ? 1 : 0;
        return ponderer$playbackSpeed == 1 && ui.isComfyReadingEnabled();
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/createmod/ponder/foundation/PonderScene;tick()V", remap = false))
    private void ponderer$tickAtPlaybackSpeed(PonderScene scene) {
        if (ponderer$isScrubbing()) {
            return;
        }
        if (ponderer$playbackSpeed == 2) {
            PonderInterpolationSnapshot.tickTwice(scene);
            if (scene == ((PonderUI) (Object) this).getActiveScene()) {
                PonderUI.ponderTicks++;
            }
        } else {
            scene.tick();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void ponderer$finishPlaybackTick(CallbackInfo ci) {
        if (ponderer$isScrubbing()) {
            PonderUI.ponderTicks = ponderer$previousPonderTicks;
            return;
        }
        PonderScene scene = ((PonderUI) (Object) this).getActiveScene();
        if (ponderer$playbackSpeed == 2 && scene == ponderer$previousScene
            && ponderer$previousTime < scene.getTotalTime() - 1 && scene.getCurrentTime() >= scene.getTotalTime()) {
            // A double tick can skip the penultimate tick checked by Ponder's completion hints.
            finishingFlashWarmup = 30;
            nextUpWarmup = 50;
        }
    }

    @Unique
    private boolean ponderer$isScrubbing() {
        return isDragging() && getFocused() instanceof PonderProgressBar;
    }

    @Inject(method = "getPartialTicks", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ponderer$freezeScrubInterpolation(CallbackInfoReturnable<Float> cir) {
        if (Minecraft.getInstance().screen instanceof PonderUI ui
            && ui.isDragging() && ui.getFocused() instanceof PonderProgressBar) {
            cir.setReturnValue(0f);
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean scrubbing = button == 0 && ponderer$isScrubbing();
        boolean handled = super.mouseReleased(mouseX, mouseY, button);
        if (scrubbing) {
            setFocused(null);
            skipCooling = 0;
            extendedTickTimer = 0;
        }
        return scrubbing || handled;
    }
}
