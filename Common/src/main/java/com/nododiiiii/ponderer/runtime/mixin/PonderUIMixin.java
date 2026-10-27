package com.nododiiiii.ponderer.runtime.mixin;

import com.nododiiiii.ponderer.runtime.api.PlaybackPreferences;
import com.nododiiiii.ponderer.runtime.api.PlaybackInputLocks;
import com.nododiiiii.ponderer.runtime.api.PlaybackUiExtensions;
import com.nododiiiii.ponderer.runtime.core.PonderSceneViewOffsetAccess;
import com.nododiiiii.ponderer.runtime.viewer.PonderRuntimeZLayers;
import com.nododiiiii.ponderer.runtime.viewer.PonderSmallHint;
import com.nododiiiii.ponderer.runtime.viewer.PonderTextListWidget;
import com.nododiiiii.ponderer.runtime.viewer.PonderUiZoomAccess;
import com.nododiiiii.ponderer.runtime.viewer.PonderUiPreviewAccess;
import com.nododiiiii.ponderer.runtime.viewer.PonderFreePreview;
import com.nododiiiii.ponderer.runtime.viewer.PonderZoomSlider;

import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.AbstractPonderScreen;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PonderUI.class, priority = 1100)
public abstract class PonderUIMixin extends AbstractPonderScreen implements PonderUiZoomAccess, PonderUiPreviewAccess {

    @Shadow(remap = false)
    protected abstract void replay();

    @Unique
    private float ponderer$userZoom = 1;

    @Unique
    private PonderZoomSlider ponderer$zoomSlider;

    @Unique
    private PonderFreePreview ponderer$freePreview;

    @Unique
    private boolean ponderer$previewSpaceHeld;

    @Unique
    private boolean ponderer$previewResumeHeld;

    @Unique
    private boolean ponderer$previewTextHeld;

    @Override
    public PonderFreePreview ponderer$getFreePreview() {
        if (ponderer$freePreview == null) {
            ponderer$freePreview = new PonderFreePreview((PonderUI) (Object) this);
        }
        return ponderer$freePreview;
    }

    @Override
    public float ponderer$getUserZoom() {
        return ponderer$userZoom;
    }

    @Override
    public void ponderer$setUserZoom(float zoom) {
        ponderer$userZoom = net.minecraft.util.Mth.clamp(zoom, PonderZoomSlider.MIN_ZOOM, PonderZoomSlider.MAX_ZOOM);
        if (ponderer$zoomSlider != null) {
            ponderer$zoomSlider.refreshValue();
        }
    }


    @Unique
    private PonderTextListWidget ponderer$textList;

    protected PonderUIMixin() {
        super();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void ponderer$addEditButton(CallbackInfo ci) {
        PonderUI self = (PonderUI) (Object) this;
        ponderer$zoomSlider = addRenderableWidget(new PonderZoomSlider(self));
        addRenderableWidget(ponderer$getFreePreview().createButton(ponderer$zoomSlider));
        PonderTextListWidget textList = new PonderTextListWidget(self);
        if (ponderer$textList != null) {
            textList.setPanelVisible(ponderer$textList.isPanelVisible());
        }
        ponderer$textList = textList;
        addRenderableWidget(textList);

        // Hijack the Create "comfy reading" button (slowMode): repurpose it as
        // the text-progress panel toggle.
        PonderUIAccessor accessor = (PonderUIAccessor) this;
        PonderButton slowMode = accessor.ponderer$getSlowMode();
        if (slowMode != null) {
            slowMode.withCallback(textList::togglePanel);
        }

    }

    @Inject(method = "renderWindow", at = @At("TAIL"), remap = false)
    private void ponderer$renderWidgetsOnTop(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, PonderRuntimeZLayers.PONDER_BUTTON_LAYER);
        // The scene is drawn at pose-z ≈ +5800 (after ponderer$liftSceneOutOfBackgroundDepth
        // pushes it to +5000 and the projection translate of +800 adds on top), with geometry
        // extents reaching pose-z ≈ +6100. Anything added with addRenderableWidget normally
        // renders at pose-z = 0 in Screen.render and fails LEQUAL against the scene's stored
        // depth, so we re-issue every renderable here above the scene envelope.
        for (GuiEventListener child : this.children()) {
            if (child instanceof Renderable renderable) {
                renderable.render(graphics, mouseX, mouseY, partialTicks);
            }
        }
        graphics.pose().popPose();
        ponderer$getFreePreview().renderButtonTooltip(graphics, mouseX, mouseY);
    }

    @Inject(method = "renderWindow", at = @At("HEAD"))
    private void ponderer$updatePreviewHover(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks,
            CallbackInfo ci) {
        if (ponderer$getFreePreview().isActive()) {
            ((PonderUIAccessor) this).ponderer$setIdentifyMode(true);
            PonderUI self = (PonderUI) (Object) this;
            self.updateIdentifiedItem(self.getActiveScene());
        }
    }

    @Redirect(method = "mouseClicked", at = @At(value = "INVOKE",
        target = "Lnet/createmod/ponder/foundation/PonderIndex;editingModeActive()Z", remap = false))
    private boolean ponderer$skipPreviewClipboard() {
        return PonderIndex.editingModeActive() && !ponderer$getFreePreview().isActive();
    }

    @Redirect(method = "renderOverlay", at = @At(value = "FIELD",
        target = "Lnet/createmod/ponder/foundation/ui/PonderUI;identifyMode:Z", ordinal = 0, remap = false))
    private boolean ponderer$keepPreviewTextVisible(PonderUI ui) {
        PonderFreePreview preview = ponderer$getFreePreview();
        // Only bypass the overlay's early return; keep the paused interpolation below it.
        return ((PonderUIAccessor) ui).ponderer$getIdentifyMode()
            && (!preview.isActive() || !preview.isTextVisible());
    }

    /**
     * Lift every draw inside {@link PonderUI#renderWidgets} above the scene's depth envelope.
     * The scene is rendered at pose-z ≈ 5800 (5000 pose + 800 projection) with geometry
     * extents reaching ~6100. Pushing the pose to {@link PonderRuntimeZLayers#PONDER_TEXT_BASELINE_LAYER}
     * (6500) means all of {@code renderSceneInformation}, the inner {@code renderOverlay} chain
     * (ponder text via TextWindowElement), {@code renderNextUp}, breadcrumbs, tag chips and
     * {@code renderHoverTooltips} land at NDC z below the scene's max NDC z and pass LEQUAL
     * regardless of whether depth-test is on or off at the time of buffer flush.
     */
    @Inject(method = "renderWidgets", at = @At("HEAD"), remap = false)
    private void ponderer$liftRenderWidgetsAboveScene(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTicks, CallbackInfo ci) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, PonderRuntimeZLayers.PONDER_TEXT_BASELINE_LAYER);
    }

    @Inject(method = "renderWidgets", at = @At("RETURN"), remap = false)
    private void ponderer$popRenderWidgetsLift(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTicks, CallbackInfo ci) {
        graphics.pose().popPose();
    }

    @Redirect(method = "seekToTime", at = @At(value = "INVOKE",
        target = "Lnet/createmod/ponder/foundation/ui/PonderUI;replay()V", remap = false), remap = false)
    private void ponderer$rewindPreviewForSeek(PonderUI ui) {
        if (ponderer$getFreePreview().isActive()) {
            // Rebuild the timeline without replay's preview and camera teardown.
            PonderScene scene = ui.getActiveScene();
            scene.begin();
            ponderer$resetCustomView(scene);
        } else {
            replay();
        }
    }

    @Inject(method = "seekToTime", at = @At("TAIL"), remap = false)
    private void ponderer$freezePreviewAfterSeek(int time, CallbackInfo ci) {
        ponderer$getFreePreview().refreshAfterSeek();
    }

    @Inject(method = "replay", at = @At("HEAD"), remap = false)
    private void ponderer$stopPreviewOnReplay(CallbackInfo ci) {
        ponderer$getFreePreview().stop();
    }

    @Inject(method = "replay", at = @At("TAIL"), remap = false)
    private void ponderer$resetViewOnReplay(CallbackInfo ci) {
        PonderUI self = (PonderUI) (Object) this;
        ponderer$resetCustomView(self.getActiveScene());
    }

    @Inject(method = "scroll", at = @At("RETURN"), remap = false)
    private void ponderer$resetViewOnScroll(boolean forward, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }
        ponderer$getFreePreview().stop();
        PonderUI self = (PonderUI) (Object) this;
        ponderer$resetCustomView(self.getActiveScene());
    }

    /**
     * PonderUI eats the scroll wheel for scene navigation before the widget
     * layer sees it. Route scroll to the text list widget first when the cursor
     * is hovering it so users can page through the list.
     */
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void ponderer$routeScrollToTextList(double mouseX, double mouseY, double scrollX, double scrollY,
            CallbackInfoReturnable<Boolean> cir) {
        for (GuiEventListener child : this.children()) {
            if (child instanceof PonderTextListWidget panel && panel.isMouseOver(mouseX, mouseY)) {
                if (panel.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                    cir.setReturnValue(true);
                }
                return;
            }
        }
        if (ponderer$getFreePreview().mouseScrolled(mouseX, mouseY, scrollY)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Add the default-toggle hint between the Comfy Reading button and its
     * label, and append our edit button's label to the shared tooltip strip.
     */
    @Inject(method = "renderHoverTooltips", at = @At("TAIL"))
    private void ponderer$renderEditButtonTooltip(GuiGraphics graphics, int tooltipColor, CallbackInfo ci) {
        int tooltipY = this.height - 16;
        PonderButton slowMode = ((PonderUIAccessor) this).ponderer$getSlowMode();
        if (slowMode != null && slowMode.isVisible() && slowMode.isHoveredOrFocused()) {
            Component hint = Component.translatable(PlaybackPreferences.textProgress()
                ? "ponderer.ui.text_progress.default_off_hint"
                : "ponderer.ui.text_progress.default_on_hint");
            // Catnip's outer border extends four pixels below the widget bounds.
            int buttonBottom = slowMode.getY() + slowMode.getHeight() + 4;
            PonderSmallHint.render(graphics, hint, slowMode.getX() + 5, buttonBottom, tooltipY, tooltipColor);
        }
    }

    private static void ponderer$resetCustomView(PonderScene scene) {
        if (!(scene instanceof PonderSceneViewOffsetAccess viewOffset)) {
            return;
        }
        viewOffset.ponderer$resetViewOffset();

        if (scene instanceof PonderSceneAccessor accessor) {
            float defaultScale = viewOffset.ponderer$getDefaultScale();
            if (!Float.isNaN(defaultScale)) {
                accessor.ponderer$setScaleFactor(defaultScale);
            }
        }
        viewOffset.ponderer$setDefaultScale(Float.NaN);
    }

    /**
     * Compute the 0-based occurrence index of the given PonderScene among all scenes
     * with the same ID in the PonderUI's scene list.
     * This is used to disambiguate when multiple packs register scenes with the same ID.
     */

    // ---- Pick mode integration ----

    /**
     * At the START of tick: reset identifyMode to false so the scene ticks
     * normally.
     * PonderUI.tick() checks {@code if (!identifyMode) { activeScene.tick(); }} —
     * if identifyMode
     * is true, the scene freezes and the structure never appears.
     * We set it false here so the scene keeps animating, then re-enable it right
     * before
     * updateIdentifiedItem (see below).
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void ponderer$tickPlaybackPreview(CallbackInfo ci) {
        ponderer$getFreePreview().tick();
    }

    /**
     * Right BEFORE updateIdentifiedItem: re-enable identifyMode so that
     * hoveredBlockPos is calculated by the raytrace.
     * After this, identifyMode stays true until the next tick's HEAD resets it.
     * During render (between ticks), identifyMode=true gives a cleaner scene view
     * (no overlays) and enables the native block-highlight tooltip.
     */


    /**
     * Intercept mouse clicks when pick mode is active.
     * Left-click on a block: pick the block's coordinates.
     * Right-click on a block: pick the adjacent block coordinates (block pos + face
     * normal).
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ponderer$onPickClick(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        if (ponderer$zoomSlider != null && ponderer$zoomSlider.mouseClicked(x, y, button)) {
            setFocused(ponderer$zoomSlider);
            setDragging(button == GLFW.GLFW_MOUSE_BUTTON_LEFT);
            cir.setReturnValue(true);
            return;
        }
        if (ponderer$getFreePreview().mouseClicked(x, y, button)) {
            cir.setReturnValue(true);
            return;
        }
        if (!PlaybackInputLocks.isLocked()) {
            PonderButton slowMode = ((PonderUIAccessor) this).ponderer$getSlowMode();
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && slowMode != null
                && slowMode.active && slowMode.visible && slowMode.isVisible() && slowMode.isMouseOver(x, y)) {
                boolean enabled = !PlaybackPreferences.textProgress();
                PlaybackPreferences.textProgress(enabled);
                ponderer$textList.setPanelVisible(enabled);
                slowMode.playDownSound(Minecraft.getInstance().getSoundManager());
                cir.setReturnValue(true);
            }
            return;
        }
    }

    /**
     * Intercept ESC to cancel pick mode and return to the editor.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_T
            && (ponderer$getFreePreview().isActive() || ponderer$previewTextHeld)) {
            if (!ponderer$previewTextHeld) {
                ponderer$previewTextHeld = true;
                ponderer$getFreePreview().toggleTextVisibility();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_Q
            && (ponderer$getFreePreview().isActive() || ponderer$previewResumeHeld)) {
            ponderer$previewResumeHeld = true;
            ponderer$getFreePreview().stop();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE && !PlaybackInputLocks.isLocked()) {
            if (!ponderer$previewSpaceHeld) {
                ponderer$previewSpaceHeld = true;
                ponderer$getFreePreview().toggle();
            }
            return true;
        }
        if (PlaybackUiExtensions.keyPressed(this, keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_T && ponderer$previewTextHeld) {
            ponderer$previewTextHeld = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_Q && ponderer$previewResumeHeld) {
            ponderer$previewResumeHeld = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            ponderer$previewSpaceHeld = false;
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return ponderer$getFreePreview().mouseDragged(button, dragX, dragY)
            || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /**
     * Render a pick hint overlay to the right of the cursor showing both click
     * coordinates.
     * Styled with opaque background and border matching editor tooltips.
     * Rendered at the highest z-level to avoid being occluded by
     * structures/tooltips.
     */


    /**
     * Fix the "vertical-line cut through the scene midline" bug that surfaces in PonderUI when
     * scenes are zoomed/translated to extreme values (and in any 3D content rendered over the
     * GUI in general).
     *
     * Root cause: PonderUI pairs {@code matrix4f.translate(0,0,800)} (projection) with
     * {@code poseStack.translate(0,0,-800)} (pose). The +800/-800 *cancel out* in clip space —
     * the net depth of the scene is identical to no-translate-at-all. With Mojang's 1.20.1 GUI
     * ortho (zNear=1000, zFar=11000) and the implicit ModelView z-translate of -10000, this lands
     * the scene at NDC z ≈ 0.76, which is right next to GUI widgets / panel backgrounds drawn at
     * pose z = 0 (NDC z = 0.8). LEQUAL depth tests then tie-fail for half the fragments,
     * producing the characteristic "midline cut" — also visible inside Ponder itself at extreme
     * zoom/move.
     *
     * Fix: override just the pose translate, keep the projection translate alone. With pose-z
     * pushed to +5000, the scene lands at NDC z ≈ -0.4 — well separated from anything at pose z=0
     * (NDC 0.8), and still solidly inside [-1, +1] even after the scene's own scale/rotation
     * extents.
     *
     * Ordinal 0 targets the first {@code PoseStack.translate(F,F,F)} call inside
     * {@code renderScene} (line 632 in upstream Ponder 1.20.1 sources, offset 140 in 1.0.92
     * bytecode). There is only one {@code renderScene} declaration in PonderUI, so the bare
     * method name is unambiguous and avoids depending on cross-loader signature remapping of
     * {@code GuiGraphics}.
     */
    @Redirect(method = "renderScene",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V",
            ordinal = 0,
            remap = true),
        remap = false)
    private void ponderer$liftSceneOutOfBackgroundDepth(PoseStack ps, float x, float y, float z) {
        ps.translate(x, y, 5000.0f);
    }

    /**
     * Reset pick state if PonderUI is closed while picking unexpectedly.
     */
    @Inject(method = "removed", at = @At("TAIL"))
    private void ponderer$onRemoved(CallbackInfo ci) {
        ponderer$previewSpaceHeld = false;
        ponderer$previewResumeHeld = false;
        ponderer$previewTextHeld = false;
        ponderer$getFreePreview().stop();
    }

    // ---- Pick mode helpers ----

    /**
     * Format a coordinate: if offset is true, display as int+0.5; otherwise just
     * the integer.
     */


    /**
     * Determine which face of a block the camera ray hits, using ray-AABB slab
     * intersection.
     * The ray is computed from the current mouse position via the scene's
     * transform.
     */

}
