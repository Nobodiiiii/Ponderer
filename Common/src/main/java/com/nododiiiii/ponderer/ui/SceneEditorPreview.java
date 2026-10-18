package com.nododiiiii.ponderer.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.mixin.PonderSceneAccessor;
import com.nododiiiii.ponderer.mixin.RenderSystemShaderLightsAccessor;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.DynamicPonderPlugin;
import com.nododiiiii.ponderer.ponder.PonderRenderCache;
import com.nododiiiii.ponderer.ponder.PonderSceneViewOffsetAccess;
import com.nododiiiii.ponderer.projector.client.ProjectorRenderContext;
import com.nododiiiii.ponderer.ui.catnip.ScreenTooltipBoxWidget;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderProgressBar;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.List;

/** Paused scene rendering with Ponder's own progress bar, keyframes and synchronous seek path. */
final class SceneEditorPreview implements AutoCloseable {
    private static final Vector3f LIGHT_0 = new Vector3f(.4f, -1, .7f).normalize();
    private static final Vector3f LIGHT_1 = new Vector3f(-.4f, -.5f, .7f).normalize();
    private static final int PROGRESS_GAP = 4;
    private static final int PROGRESS_FOOTPRINT = 26;
    private static final float OVERLAY_SCALE = .8f;
    // TextWindowElement uses the rightmost quarter of its screen, capped at 180 pixels.
    private static final int MIN_OVERLAY_LAYOUT_WIDTH = 180 * 4;

    record ViewState(float pitch, float yaw, float zoom, double panX, double panY, boolean textVisible) {}

    @Nullable private PreviewPonderUI ponder;
    @Nullable private PonderProgressBar progressBar;
    private List<Integer> stepTicks = List.of();
    private int pendingListTick = -1;
    private int x, y, width, height;
    private float pitch, yaw, zoom = 1;
    private double panX, panY;
    private int dragButton = -1;
    private boolean scrubbing;
    private long listSeekStart = -1;
    private float frozenPartialTick = 1;
    private boolean textVisible = true;
    private boolean textKeyHeld;
    private final LerpedFloat renderPitch = LerpedFloat.angular();
    private final LerpedFloat renderYaw = LerpedFloat.angular();
    private final ScreenTooltipBoxWidget resetButton;

    SceneEditorPreview() {
        resetButton = new ScreenTooltipBoxWidget(0, 0, 24, 24);
        resetButton.withPadding(4, 4).withCallback(this::resetView);
        resetButton.setMessage(Component.translatable("ponderer.ui.preview.reset"));
        resetButton.showingElement(PonderGuiTextures.ICON_PONDER_REPLAY.asStencil()
            .withElementRenderer(BoxWidget.gradientFactory.apply(resetButton)));
    }

    ViewState viewState() {
        return new ViewState(pitch, yaw, zoom, panX, panY, textVisible);
    }

    void restoreView(ViewState state) {
        pitch = state.pitch();
        yaw = state.yaw();
        zoom = state.zoom();
        panX = state.panX();
        panY = state.panY();
        textVisible = state.textVisible();
    }

    private void resetView() {
        pitch = yaw = 0;
        zoom = 1;
        panX = panY = 0;
        dragButton = -1;
    }

    void layoutOnLeft(int screenWidth, int screenHeight) {
        int previewHeight = Math.max(1, screenHeight * 3 / 5);
        setBounds(0, (screenHeight - previewHeight) / 2, screenWidth * 2 / 5, previewHeight);
    }

    Rect2i occupiedArea() {
        return new Rect2i(x, y, width, height + PROGRESS_FOOTPRINT);
    }

    void load(DslScene definition, int segmentIndex) {
        close();
        try {
            DynamicPonderPlugin.EditorScene compiled = new DynamicPonderPlugin().compileEditorScene(definition, segmentIndex);
            if (compiled == null) return;
            ponder = new PreviewPonderUI(compiled.scene());
            ponder.width = width;
            ponder.height = height;
            progressBar = new PonderProgressBar(ponder, 0, 0, 1, 1);
            stepTicks = List.copyOf(compiled.stepTicks());
            seekStep(0);
            layoutProgressBar();
        } catch (RuntimeException e) {
            close();
            LogUtils.getLogger().warn("Unable to compile scene editor preview for {} segment {}", definition.id, segmentIndex, e);
        }
    }

    void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        if (ponder != null) {
            ponder.width = this.width;
            ponder.height = this.height;
        }
        resetButton.setX(x + this.width - resetButton.getWidth() - 4);
        resetButton.setY(y + 4);
        layoutProgressBar();
    }

    private void layoutProgressBar() {
        if (progressBar == null) return;
        int barWidth = Math.max(1, Math.round((width - 24) * .8f));
        progressBar.setX(x + (width - barWidth) / 2);
        progressBar.setY(y + height + PROGRESS_GAP);
        progressBar.setWidth(barWidth);
    }

    void tick() {
        // Only the progress indicator ticks; the scene stays paused between seeks.
        if (progressBar != null) progressBar.tick();
        resetButton.tick();
    }

    int stepAtCursor() {
        int tick = ponder == null ? 0 : ponder.getActiveScene().getCurrentTime();
        int step = 0;
        for (int i = 0; i < stepTicks.size(); i++) {
            if (stepTicks.get(i) > tick) break;
            step = i;
        }
        return step;
    }

    void seekStep(int index) {
        if (!scrubbing && index >= 0 && index < stepTicks.size()) {
            // Scroll events in the same frame replace one pending seek; never queue replays.
            pendingListTick = stepTicks.get(index);
        }
    }

    boolean isScrubbing() { return scrubbing; }

    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (ponder == null) {
            graphics.drawCenteredString(Minecraft.getInstance().font,
                Component.translatable("ponderer.ui.scene_editor.preview_unavailable"),
                x + width / 2, y + height / 2, 0xAAAAAA);
            return;
        }
        if (pendingListTick >= 0) {
            int previousTick = ponder.getActiveScene().getCurrentTime();
            ponder.seekToTime(pendingListTick);
            if (previousTick != ponder.getActiveScene().getCurrentTime()) {
                // Interpolate only the final native tick, never cross-fade whole scene images.
                listSeekStart = previousTick > 0 ? Util.getMillis() : -1;
                frozenPartialTick = 1;
            }
            pendingListTick = -1;
        }
        float scenePartialTick = listSeekStart < 0 ? frozenPartialTick
            : Mth.clamp((Util.getMillis() - listSeekStart) / 50f, 0, 1);
        renderScene(graphics, scenePartialTick);
        // Use the native widget unchanged, including its keyframe markers and drag/snap mixin.
        progressBar.render(graphics, mouseX, mouseY, partialTicks);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0, 0, PonderRuntimeZLayers.PONDER_BUTTON_LAYER + 500);
            resetButton.render(graphics, mouseX, mouseY, partialTicks);
        } finally {
            graphics.pose().popPose();
        }
    }

    void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (ponder == null || !resetButton.isMouseOver(mouseX, mouseY)) return;
        graphics.flush();
        graphics.pose().pushPose();
        try {
            RenderSystem.disableDepthTest();
            graphics.pose().translate(0, 0, PonderRuntimeZLayers.PONDER_BUTTON_LAYER + 800);
            graphics.renderComponentTooltip(Minecraft.getInstance().font,
                PonderFreePreview.controlsTooltip("ponderer.ui.preview.reset", textVisible), mouseX, mouseY);
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
    }

    private void renderScene(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        PonderScene scene = ponder.getActiveScene();
        var transform = scene.getTransform();
        var originalPitch = transform.xRotation;
        var originalYaw = transform.yRotation;
        var view = (PonderSceneViewOffsetAccess) scene;
        var accessor = (PonderSceneAccessor) scene;
        float originalScale = scene.getScaleFactor();
        boolean scaleOverride = view.ponderer$isScaleOverrideActive();
        graphics.flush();
        Vector3f[] lights = RenderSystemShaderLightsAccessor.ponderer$getShaderLightDirections();
        Vector3f light0 = new Vector3f(lights[0]), light1 = new Vector3f(lights[1]);
        var previousCamera = mc.cameraEntity;
        var pose = graphics.pose();
        graphics.enableScissor(x, y, x + width, y + height);
        boolean sceneScissor = true;
        pose.pushPose();
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setupLevelDiffuseLighting(LIGHT_0, LIGHT_1);
            var bounds = scene.getBounds();
            float scale = scaleOverride ? view.ponderer$getScaleOverride().getValue(partialTick) : originalScale;
            double fit = Math.min(width, height) * .68 / Math.max(1,
                Math.max(bounds.getYSpan(), Math.max(bounds.getXSpan(), bounds.getZSpan())));
            // Temporarily share the native transform with both the structure and text anchors.
            // Restore the scripted chasers and scale afterward so seeking stays deterministic.
            transform.xRotation = renderPitch.startWithValue(Mth.clamp(originalPitch.getValue(partialTick) + pitch, -89, 89));
            transform.yRotation = renderYaw.startWithValue(originalYaw.getValue(partialTick) + yaw);
            view.ponderer$setScaleOverrideActive(false);
            accessor.ponderer$setScaleFactor((float) fit * zoom * scale / 30);
            transform.updateScreenParams(width + (int) Math.round(panX * 2), height + (int) Math.round(panY * 2), 0);
            pose.translate(x, y, 0);
            pose.pushPose();
            try {
                pose.translate(0, 0, 4800);
                transform.apply(pose, partialTick);
                transform.updateSceneRVE(partialTick);
                var buffer = DefaultSuperRenderTypeBuffer.getInstance();
                ProjectorRenderContext.runWithFrameTime(scene.getCurrentTime(), partialTick,
                    () -> scene.renderScene(buffer, graphics, partialTick));
                buffer.draw();
            } finally {
                pose.popPose();
            }
            graphics.flush();
            graphics.disableScissor();
            sceneScissor = false;
            RenderSystem.disableDepthTest();
            pose.translate(0, 0, PonderRuntimeZLayers.PONDER_TEXT_BASELINE_LAYER);
            if (textVisible) {
                int overlayWidth = Math.max(MIN_OVERLAY_LAYOUT_WIDTH, Mth.ceil(width / OVERLAY_SCALE));
                int overlayHeight = Mth.ceil(height / OVERLAY_SCALE);
                ponder.width = overlayWidth;
                ponder.height = overlayHeight;
                pose.translate((width - overlayWidth * OVERLAY_SCALE) / 2,
                    (height - overlayHeight * OVERLAY_SCALE) / 2, 0);
                pose.scale(OVERLAY_SCALE, OVERLAY_SCALE, 1);
                // Compensate the scene projection so scaled text and controls retain their world anchors.
                accessor.ponderer$setScaleFactor(scene.getScaleFactor() / OVERLAY_SCALE);
                transform.updateScreenParams(overlayWidth + (int) Math.round(panX * 2 / OVERLAY_SCALE),
                    overlayHeight + (int) Math.round(panY * 2 / OVERLAY_SCALE), 0);
                ProjectorRenderContext.runWithFrameTime(scene.getCurrentTime(), partialTick,
                    () -> scene.renderOverlay(ponder, graphics, partialTick));
            }
        } finally {
            ponder.width = width;
            ponder.height = height;
            transform.xRotation = originalPitch;
            transform.yRotation = originalYaw;
            accessor.ponderer$setScaleFactor(originalScale);
            view.ponderer$setScaleOverrideActive(scaleOverride);
            transform.updateScreenParams(width, height, 0);
            mc.cameraEntity = previousCamera;
            pose.popPose();
            if (sceneScissor) graphics.disableScissor();
            RenderSystem.setShaderLights(light0, light1);
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
        }
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ponder == null) return false;
        if (resetButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (progressBar.mouseClicked(mouseX, mouseY, button)) {
            pendingListTick = -1;
            scrubbing = true;
            listSeekStart = -1;
            frozenPartialTick = 0;
            progressBar.setFocused(true);
            return true;
        }
        if (containsModel(mouseX, mouseY) && !resetButton.isMouseOver(mouseX, mouseY) && (button == 0 || button == 1)) {
            dragButton = button;
            return true;
        }
        return false;
    }

    boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (scrubbing && button == 0) {
            return progressBar.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        if (dragButton != button) return false;
        if (button == 0) {
            float basePitch = ponder.getActiveScene().getTransform().xRotation.getValue(frozenPartialTick);
            pitch = Mth.clamp(basePitch + pitch - (float) deltaY, -89, 89) - basePitch;
            yaw = Mth.wrapDegrees(yaw + (float) deltaX);
        } else {
            panX += deltaX;
            panY += deltaY;
        }
        return true;
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean consumed = dragButton == button || scrubbing && button == 0;
        if (dragButton == button) dragButton = -1;
        if (scrubbing && button == 0) {
            progressBar.mouseReleased(mouseX, mouseY, button);
            progressBar.setFocused(false);
            scrubbing = false;
        }
        return consumed;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (ponder == null || !containsModel(mouseX, mouseY)) return false;
        zoom = Mth.clamp((float) (zoom * Math.pow(1.1, delta)), .1f, 8);
        return true;
    }

    private boolean containsModel(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    boolean keyPressed(int keyCode) {
        if (keyCode != GLFW.GLFW_KEY_T || ponder == null) return false;
        if (!textKeyHeld) textVisible = !textVisible;
        textKeyHeld = true;
        return true;
    }

    boolean keyReleased(int keyCode) {
        if (keyCode != GLFW.GLFW_KEY_T || !textKeyHeld) return false;
        textKeyHeld = false;
        return true;
    }

    @Override public void close() {
        if (ponder != null) PonderRenderCache.releaseDiscardedScene(ponder.getActiveScene());
        ponder = null;
        progressBar = null;
        stepTicks = List.of();
        pendingListTick = -1;
        listSeekStart = -1;
        frozenPartialTick = 1;
        scrubbing = false;
        dragButton = -1;
        textKeyHeld = false;
    }

    /** Provides the native progress bar its PonderUI owner without initializing any Ponder UI chrome. */
    private static final class PreviewPonderUI extends PonderUI {
        private final float initialScale;

        PreviewPonderUI(PonderScene scene) {
            super(List.of(scene));
            initialScale = scene.getScaleFactor();
        }

        @Override public Font getFontRenderer() {
            return Minecraft.getInstance().font;
        }

        @Override public void seekToTime(int time) {
            int target = Mth.clamp(time, 0, getActiveScene().getTotalTime());
            if (target != getActiveScene().getCurrentTime()) {
                // The same synchronous rewind/seek path used by the full Ponder screen.
                ProjectorRenderContext.run(() -> super.seekToTime(target));
            }
        }

        @Override protected void replay() {
            // Keep the editor's free camera and avoid the full screen's Shift-to-recompile action.
            PonderScene scene = getActiveScene();
            ((PonderSceneAccessor) scene).ponderer$setScaleFactor(initialScale);
            var view = (PonderSceneViewOffsetAccess) scene;
            view.ponderer$resetViewOffset();
            view.ponderer$setDefaultScale(Float.NaN);
            scene.begin();
        }
    }
}
