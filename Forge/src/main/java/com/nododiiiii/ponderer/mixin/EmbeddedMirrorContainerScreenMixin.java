package com.nododiiiii.ponderer.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nododiiiii.ponderer.forge.sticksnapshot.client.ClientInputHandler;
import com.nododiiiii.ponderer.ui.PonderRuntimeZLayers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class EmbeddedMirrorContainerScreenMixin {
    @Unique
    private boolean ponderer$embeddedWholeGuiPushed;
    @Unique
    private boolean ponderer$embeddedBgPushed;
    @Unique
    private boolean ponderer$embeddedWidgetPushed;
    @Unique
    private boolean ponderer$embeddedSlotPushed;
    @Unique
    private boolean ponderer$embeddedFloatingItemPushed;
    @Unique
    private boolean ponderer$embeddedLabelsPushed;
    @Unique
    private boolean ponderer$embeddedTooltipPushed;

    @Inject(method = "render", at = @At("HEAD"))
    private void ponderer$raiseWholeMekanismGui(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        if (!ponderer$isEmbeddedMirrorScreen() || !ponderer$isSelfManagedForegroundScreen()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, PonderRuntimeZLayers.MEKANISM_EMBEDDED_RENDER_BIAS);
        ponderer$embeddedWholeGuiPushed = true;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void ponderer$restoreWholeMekanismGui(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        if (!ponderer$embeddedWholeGuiPushed) {
            return;
        }
        graphics.pose().popPose();
        ponderer$embeddedWholeGuiPushed = false;
    }

    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"
        )
    )
    private void ponderer$raiseEmbeddedBackground(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        Screen mirror = ClientInputHandler.getEmbeddedMirrorScreen();
        if (!ClientInputHandler.isRenderingEmbeddedMirror() || mirror == null || mirror != (Object) this) {
            return;
        }
        graphics.flush();
        RenderSystem.disableDepthTest();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, PonderRuntimeZLayers.EMBEDDED_GUI_BACKGROUND_LAYER);
        ponderer$embeddedBgPushed = true;
    }

    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
            shift = At.Shift.AFTER
        )
    )
    private void ponderer$restoreEmbeddedBackgroundZ(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        if (!ponderer$embeddedBgPushed) {
            return;
        }
        graphics.flush();
        RenderSystem.disableDepthTest();
        graphics.pose().popPose();
        ponderer$embeddedBgPushed = false;
    }

    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
        )
    )
    private void ponderer$raiseEmbeddedWidgets(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        if (!ponderer$isEmbeddedMirrorScreen()) {
            return;
        }
        graphics.flush();
        RenderSystem.disableDepthTest();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, PonderRuntimeZLayers.EMBEDDED_GUI_WIDGET_LAYER);
        ponderer$embeddedWidgetPushed = true;
    }

    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            shift = At.Shift.AFTER
        )
    )
    private void ponderer$restoreEmbeddedWidgetsZ(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        if (!ponderer$embeddedWidgetPushed) {
            return;
        }
        graphics.flush();
        RenderSystem.disableDepthTest();
        graphics.pose().popPose();
        ponderer$embeddedWidgetPushed = false;
    }

    @Inject(method = "renderSlot", at = @At("HEAD"))
    private void ponderer$raiseEmbeddedSlot(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        if (!ponderer$isEmbeddedMirrorScreen()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, PonderRuntimeZLayers.embeddedSlotPoseZ());
        ponderer$embeddedSlotPushed = true;
    }

    @Inject(method = "renderSlot", at = @At("RETURN"))
    private void ponderer$restoreEmbeddedSlotZ(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        if (!ponderer$embeddedSlotPushed) {
            return;
        }
        graphics.pose().popPose();
        ponderer$embeddedSlotPushed = false;
    }

    @Inject(method = "renderFloatingItem", at = @At("HEAD"))
    private void ponderer$raiseEmbeddedFloatingItem(GuiGraphics graphics, ItemStack stack, int x, int y,
            String altText, CallbackInfo ci) {
        if (!ponderer$isEmbeddedMirrorScreen()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, PonderRuntimeZLayers.embeddedFloatingItemPoseZ());
        ponderer$embeddedFloatingItemPushed = true;
    }

    @Inject(method = "renderFloatingItem", at = @At("RETURN"))
    private void ponderer$restoreEmbeddedFloatingItemZ(GuiGraphics graphics, ItemStack stack, int x, int y,
            String altText, CallbackInfo ci) {
        if (!ponderer$embeddedFloatingItemPushed) {
            return;
        }
        graphics.pose().popPose();
        ponderer$embeddedFloatingItemPushed = false;
    }

    @Inject(method = "renderLabels", at = @At("HEAD"))
    private void ponderer$raiseEmbeddedLabels(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (!ponderer$isEmbeddedMirrorScreen() || ponderer$isSelfManagedForegroundScreen()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, PonderRuntimeZLayers.EMBEDDED_GUI_OVERLAY_LAYER);
        ponderer$embeddedLabelsPushed = true;
    }

    @Inject(method = "renderLabels", at = @At("RETURN"))
    private void ponderer$restoreEmbeddedLabels(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (!ponderer$embeddedLabelsPushed) {
            return;
        }
        graphics.pose().popPose();
        ponderer$embeddedLabelsPushed = false;
    }

    @Inject(method = "renderTooltip", at = @At("HEAD"))
    private void ponderer$raiseEmbeddedTooltip(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (!ponderer$isEmbeddedMirrorScreen() || ponderer$isSelfManagedForegroundScreen()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, PonderRuntimeZLayers.embeddedTooltipPoseZ());
        ponderer$embeddedTooltipPushed = true;
    }

    @Inject(method = "renderTooltip", at = @At("RETURN"))
    private void ponderer$restoreEmbeddedTooltip(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (!ponderer$embeddedTooltipPushed) {
            return;
        }
        graphics.pose().popPose();
        ponderer$embeddedTooltipPushed = false;
    }

    @Unique
    private boolean ponderer$isEmbeddedMirrorScreen() {
        Screen mirror = ClientInputHandler.getEmbeddedMirrorScreen();
        return ClientInputHandler.isRenderingEmbeddedMirror() && mirror != null && mirror == (Object) this;
    }

    @Unique
    private boolean ponderer$isSelfManagedForegroundScreen() {
        return ((Object) this).getClass().getName().startsWith("mekanism.client.gui.");
    }
}
