package com.nododiiiii.ponderer.mixin;

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
    @Shadow(remap = false) private int extendedTickLength;
    @Shadow(remap = false) private int extendedTickTimer;
    @Shadow(remap = false) private int skipCooling;
    @Shadow(remap = false) private int finishingFlashWarmup;
    @Shadow(remap = false) private int nextUpWarmup;

    @Unique private double ponderer$playbackSpeed = 1;
    @Unique private PonderSpeedButton ponderer$halfSpeed;
    @Unique private PonderSpeedButton ponderer$doubleSpeed;
    @Unique private PonderScene ponderer$previousScene;
    @Unique private int ponderer$previousTime;
    @Unique private int ponderer$previousPonderTicks;

    protected PonderPlaybackMixin() {
        super(CommonComponents.EMPTY);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void ponderer$addSpeedButtons(CallbackInfo ci) {
        ponderer$halfSpeed = addRenderableWidget(new PonderSpeedButton(left.getX() - 28, left.getY(), "÷2",
            Component.translatable("ponderer.ui.playback.half"), () -> ponderer$toggleSpeed(.5)));
        ponderer$doubleSpeed = addRenderableWidget(new PonderSpeedButton(right.getX() + 28, right.getY(), "×2",
            Component.translatable("ponderer.ui.playback.double"), () -> ponderer$toggleSpeed(2)));
        ponderer$updateSpeedButtons();
    }

    @Unique
    private void ponderer$toggleSpeed(double speed) {
        ponderer$playbackSpeed = ponderer$playbackSpeed == speed ? 1 : speed;
        extendedTickTimer = 0;
        ponderer$updateSpeedButtons();
    }

    @Unique
    private void ponderer$updateSpeedButtons() {
        ponderer$halfSpeed.withCustomTheme(ponderer$playbackSpeed == .5 ? PonderButton.COLOR_CLICK : PonderButton.COLOR_IDLE,
            null, null, null);
        ponderer$doubleSpeed.withCustomTheme(ponderer$playbackSpeed == 2 ? PonderButton.COLOR_CLICK : PonderButton.COLOR_IDLE,
            null, null, null);
    }

    @Inject(method = "renderHoverTooltips", at = @At("TAIL"))
    private void ponderer$renderSpeedTooltip(GuiGraphics graphics, int tooltipColor, CallbackInfo ci) {
        for (PonderSpeedButton button : new PonderSpeedButton[]{ponderer$halfSpeed, ponderer$doubleSpeed}) {
            if (button != null && button.isVisible() && button.isHoveredOrFocused()) {
                graphics.drawCenteredString(Minecraft.getInstance().font, button.getMessage(),
                    button.getX() + button.getWidth() / 2, height - 16, tooltipColor);
            }
        }
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
        scene.tick();
        if (ponderer$playbackSpeed == 2) {
            scene.tick();
            if (scene == ((PonderUI) (Object) this).getActiveScene()) {
                PonderUI.ponderTicks++;
            }
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
