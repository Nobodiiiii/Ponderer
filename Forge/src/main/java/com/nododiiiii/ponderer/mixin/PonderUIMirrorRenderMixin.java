package com.nododiiiii.ponderer.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.forge.sticksnapshot.client.ClientInputHandler;
import com.nododiiiii.ponderer.ui.InterfaceSlotOverlayRenderer;
import com.nododiiiii.ponderer.ui.PonderRuntimeZLayers;
import net.createmod.ponder.foundation.ui.PonderProgressBar;
import net.createmod.ponder.foundation.ui.PonderUI;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// priority > default (1000) so the renderWidgets TAIL inject below is applied AFTER
// PonderUIMixin's popRenderWidgetsLift — at runtime that means it runs FIRST, while
// the outer pose-z lift is still on the stack, so we can pop/push it cleanly.
@Mixin(value = PonderUI.class, priority = 1100)
public abstract class PonderUIMirrorRenderMixin {
    @Inject(method = "renderScene", at = @At("HEAD"), cancellable = true, remap = false)
    private void ponderer$skipStructureWhenMirrorAttached(GuiGraphics graphics, int mouseX, int mouseY, int i,
            float partialTicks, CallbackInfo ci) {
        Screen mirror = ClientInputHandler.getEmbeddedMirrorScreen();
        if (mirror == null) {
            return;
        }
        if (!ClientInputHandler.shouldRenderEmbeddedMirror()) {
            return;
        }
        ci.cancel();
    }

    @Inject(
        method = "renderWidgets",
        at = @At(
            value = "INVOKE",
            target = "Lnet/createmod/ponder/foundation/ui/PonderUI;renderSceneOverlay(Lnet/minecraft/client/gui/GuiGraphics;FFF)V"
        ),
        remap = false
    )
    private void ponderer$renderMirrorBetweenUiLayers(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTicks, CallbackInfo ci) {
        Screen mirror = ClientInputHandler.getEmbeddedMirrorScreen();
        if (mirror == null || !ClientInputHandler.shouldRenderEmbeddedMirror()) {
            return;
        }

        // PonderUIMixin#ponderer$liftRenderWidgetsAboveScene pushed pose-z by
        // PONDER_TEXT_BASELINE_LAYER (+6500) at renderWidgets HEAD. The embedded
        // mirror's own EMBEDDED_GUI_* layers already carry SCENE_LIFT_BIAS (+6000),
        // so stacking them on top of +6500 lands the mirror past the GUI projection's
        // near clip (~+10000) and the whole UI disappears (vanilla crafting/chest/etc.).
        // Drop the outer lift while the mirror renders, then restore it so the rest
        // of renderWidgets — and ponderer$popRenderWidgetsLift at RETURN — see the
        // pose stack they expect.
        graphics.flush();
        graphics.pose().popPose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        ClientInputHandler.beginEmbeddedMirrorRender();
        try {
            mirror.render(graphics, mouseX, mouseY, partialTicks);
            graphics.flush();
        } finally {
            ClientInputHandler.endEmbeddedMirrorRender();
        }

        RenderSystem.disableDepthTest();
        InterfaceSlotOverlayRenderer.render(graphics, mirror);
        ClientInputHandler.renderDraggedSlotBinding(graphics, mouseX, mouseY);

        if (JeiCompat.shouldRenderPonderUiOverlayManually((Screen) (Object) this)) {
            JeiCompat.renderPonderUiOverlay((Screen) (Object) this, graphics, mouseX, mouseY, partialTicks);
        }

        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, PonderRuntimeZLayers.PONDER_TEXT_BASELINE_LAYER);
    }

    @Inject(method = "renderWidgets", at = @At("TAIL"), remap = false)
    private void ponderer$renderMirrorForegroundArtifacts(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTicks, CallbackInfo ci) {
        Screen mirror = ClientInputHandler.getEmbeddedMirrorScreen();
        if (mirror == null || !ClientInputHandler.shouldRenderEmbeddedMirror()) {
            return;
        }

        // Same lift dance as above — the tooltip layers (TOOLTIP_LAYER = +7000)
        // would otherwise stack to ~+13500 and get near-clipped. This inject's
        // priority (1100) ensures it runs before PonderUIMixin#popRenderWidgetsLift,
        // so the outer lift is still on the stack here.
        graphics.flush();
        graphics.pose().popPose();

        RenderSystem.disableDepthTest();
        if (JeiCompat.shouldRenderPonderUiOverlayManually((Screen) (Object) this)) {
            JeiCompat.renderPonderUiTooltips((Screen) (Object) this, graphics, mouseX, mouseY);
        }
        InterfaceSlotOverlayRenderer.renderTooltip(graphics, mirror, mouseX, mouseY);
        RenderSystem.enableDepthTest();

        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, PonderRuntimeZLayers.PONDER_TEXT_BASELINE_LAYER);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void ponderer$tickEmbeddedMirror(CallbackInfo ci) {
        boolean hideProgress = ClientInputHandler.isAutoReplayWaitActive();
        Screen self = (Screen) (Object) this;
        for (GuiEventListener child : self.children()) {
            if (child instanceof PonderProgressBar bar) {
                bar.visible = !hideProgress;
            }
        }

        Screen mirror = ClientInputHandler.getEmbeddedMirrorScreen();
        if (mirror != null) {
            mirror.tick();
        }
    }

    @Inject(method = "replay", at = @At("HEAD"), remap = false)
    private void ponderer$closeEmbeddedOnReplay(CallbackInfo ci) {
        InterfaceSlotOverlayRenderer.clearRuntimeBindings();
        if (ClientInputHandler.hasEmbeddedMirrorScreen()) {
            ClientInputHandler.closeEmbeddedMirrorFromPonder("ponder-replay");
        }
    }

    @Inject(method = "scroll", at = @At("RETURN"), remap = false)
    private void ponderer$closeEmbeddedOnSceneSwitch(boolean forward, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }
        InterfaceSlotOverlayRenderer.clearRuntimeBindings();

        PonderUI self = (PonderUI) (Object) this;
        if (ponderer$isShowInterfaceScene(self, self.getActiveScene())) {
            ((PonderUIAccessor) (Object) self).ponderer$getLazyIndex()
                .startWithValue(((PonderUIAccessor) (Object) self).ponderer$getIndex());
        }

        if (ClientInputHandler.hasEmbeddedMirrorScreen()) {
            ClientInputHandler.closeEmbeddedMirrorFromPonder("ponder-scene-switch");
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void ponderer$closeEmbeddedWhenPonderRemoved(CallbackInfo ci) {
        InterfaceSlotOverlayRenderer.clearRuntimeBindings();
        if (ClientInputHandler.hasEmbeddedMirrorScreen()) {
            ClientInputHandler.closeEmbeddedMirrorFromPonder("ponder-removed");
        }
    }

    private static boolean ponderer$isShowInterfaceScene(PonderUI ui, PonderScene target) {
        PonderUIAccessor accessor = (PonderUIAccessor) (Object) ui;
        int occurrence = ponderer$computeOccurrenceIndex(accessor, target);
        SceneRuntime.SceneMatch match = SceneRuntime.findBySceneId(target.getId(), occurrence);
        if (match == null) {
            return false;
        }

        DslScene scene = match.scene();
        int index = match.sceneIndex();
        if (scene == null || scene.scenes == null || index < 0 || index >= scene.scenes.size()) {
            return false;
        }

        DslScene.SceneSegment segment = scene.scenes.get(index);
        if (segment == null || segment.steps == null) {
            return false;
        }

        for (DslScene.DslStep step : segment.steps) {
            if (step == null || step.type == null || step.type.isBlank()) {
                continue;
            }
            return "show_interface".equalsIgnoreCase(step.type);
        }
        return false;
    }

    private static int ponderer$computeOccurrenceIndex(PonderUIAccessor accessor, PonderScene target) {
        int occurrence = 0;
        for (PonderScene scene : accessor.ponderer$getScenes()) {
            if (scene == target) {
                return occurrence;
            }
            if (scene.getId().equals(target.getId())) {
                occurrence++;
            }
        }
        return 0;
    }
}
