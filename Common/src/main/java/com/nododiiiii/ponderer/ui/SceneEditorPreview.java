package com.nododiiiii.ponderer.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.nododiiiii.ponderer.mixin.PonderSceneAccessor;
import com.nododiiiii.ponderer.mixin.RenderSystemShaderLightsAccessor;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.DynamicPonderPlugin;
import com.nododiiiii.ponderer.ponder.PonderRenderCache;
import com.nododiiiii.ponderer.ponder.PonderSceneViewOffsetAccess;
import com.nododiiiii.ponderer.projector.client.ProjectorRenderContext;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderProgressBar;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.List;

/** Paused scene rendering with Ponder's own progress bar, keyframes and synchronous seek path. */
final class SceneEditorPreview implements AutoCloseable {
    private static final Vector3f LIGHT_0 = new Vector3f(.4f, -1, .7f).normalize();
    private static final Vector3f LIGHT_1 = new Vector3f(-.4f, -.5f, .7f).normalize();

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

    void load(DslScene definition, int segmentIndex) {
        close();
        try {
            DynamicPonderPlugin.EditorScene compiled = new DynamicPonderPlugin().compileEditorScene(definition, segmentIndex);
            if (compiled == null) return;
            ponder = new PreviewPonderUI(compiled.scene());
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
        layoutProgressBar();
    }

    private void layoutProgressBar() {
        if (progressBar == null) return;
        int barWidth = Math.max(1, Math.round((width - 24) * .8f));
        progressBar.setX(x + (width - barWidth) / 2);
        progressBar.setY(y + height - 34);
        progressBar.setWidth(barWidth);
    }

    void tick() {
        // Only the progress indicator ticks; the scene stays paused between seeks.
        if (progressBar != null) progressBar.tick();
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
        int modelHeight = Math.max(1, height - 52);
        if (ponder == null) {
            graphics.drawCenteredString(Minecraft.getInstance().font,
                Component.translatable("ponderer.ui.scene_editor.preview_unavailable"),
                x + width / 2, y + modelHeight / 2, 0xAAAAAA);
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
        renderScene(graphics, modelHeight, scenePartialTick);
        // Use the native widget unchanged, including its keyframe markers and drag/snap mixin.
        progressBar.render(graphics, mouseX, mouseY, partialTicks);
    }

    private void renderScene(GuiGraphics graphics, int modelHeight, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        PonderScene scene = ponder.getActiveScene();
        graphics.flush();
        Vector3f[] lights = RenderSystemShaderLightsAccessor.ponderer$getShaderLightDirections();
        Vector3f light0 = new Vector3f(lights[0]), light1 = new Vector3f(lights[1]);
        var previousCamera = mc.cameraEntity;
        var pose = graphics.pose();
        graphics.enableScissor(x, y, x + width, y + modelHeight);
        pose.pushPose();
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setupLevelDiffuseLighting(LIGHT_0, LIGHT_1);
            var transform = scene.getTransform();
            var bounds = scene.getBounds();
            var view = (PonderSceneViewOffsetAccess) scene;
            float scale = view.ponderer$isScaleOverrideActive()
                ? view.ponderer$getScaleOverride().getValue(partialTick) : scene.getScaleFactor();
            double fit = Math.min(width, modelHeight) * .68 / Math.max(1,
                Math.max(bounds.getYSpan(), Math.max(bounds.getXSpan(), bounds.getZSpan())));
            pose.translate(x + width / 2.0 + panX, y + modelHeight * .65 + panY, 5000);
            pose.mulPose(Axis.XP.rotationDegrees(Mth.clamp(transform.xRotation.getValue(partialTick) + pitch, -89, 89)));
            pose.mulPose(Axis.YP.rotationDegrees(transform.yRotation.getValue(partialTick) + yaw));
            UIRenderHelper.flipForGuiRender(pose);
            float unit = (float) fit * zoom * scale;
            pose.scale(unit, unit, unit);
            // Retain the native base-plate origin for scripted zoom/point-of-interest changes.
            pose.translate(scene.getBasePlateSize() / -2.0 - scene.getBasePlateOffsetX(),
                -1 + scene.getYOffset(), scene.getBasePlateSize() / -2.0 - scene.getBasePlateOffsetZ());
            pose.translate(view.ponderer$getViewOffsetX().getValue(partialTick), view.ponderer$getViewOffsetY().getValue(partialTick),
                view.ponderer$getViewOffsetZ().getValue(partialTick));
            var buffer = DefaultSuperRenderTypeBuffer.getInstance();
            ProjectorRenderContext.runWithFrameTime(scene.getCurrentTime(), partialTick,
                () -> scene.renderScene(buffer, graphics, partialTick));
            buffer.draw();
        } finally {
            mc.cameraEntity = previousCamera;
            pose.popPose();
            graphics.disableScissor();
            RenderSystem.setShaderLights(light0, light1);
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
        }
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ponder == null) return false;
        if (progressBar.mouseClicked(mouseX, mouseY, button)) {
            pendingListTick = -1;
            scrubbing = true;
            listSeekStart = -1;
            frozenPartialTick = 0;
            progressBar.setFocused(true);
            return true;
        }
        if (containsModel(mouseX, mouseY) && (button == 0 || button == 1)) {
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
            pitch = Mth.clamp(pitch - (float) deltaY, -54, 124);
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
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height - 52;
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
    }

    /** Provides the native progress bar its PonderUI owner without initializing any Ponder UI chrome. */
    private static final class PreviewPonderUI extends PonderUI {
        private final float initialScale;

        PreviewPonderUI(PonderScene scene) {
            super(List.of(scene));
            initialScale = scene.getScaleFactor();
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
