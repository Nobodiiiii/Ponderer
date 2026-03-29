package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.compat.jei.PondererJeiPlugin;
import com.nododiiiii.ponderer.forge.sticksnapshot.client.ClientInputHandler;
import com.nododiiiii.ponderer.ui.InterfaceSlotOverlayRenderer;
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

@Mixin(PonderUI.class)
public abstract class PonderUIMirrorRenderMixin {

    @Inject(method = "renderScene", at = @At("HEAD"), cancellable = true, remap = false)
    private void ponderer$renderMirrorInsteadOfStructure(GuiGraphics graphics, int mouseX, int mouseY, int i,
            float partialTicks, CallbackInfo ci) {
        Screen mirror = ClientInputHandler.getEmbeddedMirrorScreen();
        if (mirror == null) {
            return;
        }
        if (!ClientInputHandler.shouldRenderEmbeddedMirror()) {
            return;
        }
        mirror.render(graphics, mouseX, mouseY, partialTicks);
        InterfaceSlotOverlayRenderer.render(graphics, mirror);
        ClientInputHandler.renderDraggedSlotBinding(graphics, mouseX, mouseY);
        PondererJeiPlugin.renderGhostIngredientDrag(graphics, mouseX, mouseY);
        InterfaceSlotOverlayRenderer.renderTooltip(graphics, mirror, mouseX, mouseY);
        ci.cancel();
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
