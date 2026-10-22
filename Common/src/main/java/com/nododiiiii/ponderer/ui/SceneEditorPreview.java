package com.nododiiiii.ponderer.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
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
import net.createmod.catnip.outliner.AABBOutline;
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.WorldSectionElementImpl;
import net.createmod.ponder.foundation.ui.PonderProgressBar;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Scene rendering with paused seeking and bounded playback of the edited step's native effects. */
final class SceneEditorPreview implements AutoCloseable {
    private static final Vector3f LIGHT_0 = new Vector3f(.4f, -1, .7f).normalize();
    private static final Vector3f LIGHT_1 = new Vector3f(-.4f, -.5f, .7f).normalize();
    private static final int PROGRESS_GAP = 4;
    private static final int PROGRESS_FOOTPRINT = 26;
    private static final float OVERLAY_SCALE = .8f;
    // Match the original view after two upward wheel steps (1.1 * 1.1).
    private static final float DEFAULT_ZOOM = 1.21f;
    // TextWindowElement uses the rightmost quarter of its screen, capped at 180 pixels.
    private static final int MIN_OVERLAY_LAYOUT_WIDTH = 180 * 4;

    record ViewState(float pitch, float yaw, float zoom, double panX, double panY, boolean textVisible) {}

    enum PointPickMode { FACE_CENTER, BLOCK, ADJACENT_BLOCK }

    private record PointPick(PointPickMode mode, Consumer<Vec3> onPicked) {}
    private record PointPickResult(Vec3 point, Direction face, WorldSectionElement section) {}
    private record SectionHit(WorldSectionElement section, BlockHitResult hit, float depth) {}

    @Nullable private PreviewPonderUI ponder;
    @Nullable private PonderProgressBar progressBar;
    private List<Integer> stepTicks = List.of();
    private boolean playingStep;
    private int pendingListTick = -1;
    private int x, y, width, height;
    private float pitch, yaw, zoom = DEFAULT_ZOOM;
    private double panX, panY;
    private int dragButton = -1;
    private boolean scrubbing;
    private long listSeekStart = -1;
    private float frozenPartialTick = 1;
    private boolean textVisible = true;
    private boolean textKeyHeld;
    @Nullable private PointPick pointPick;
    @Nullable private PointPickResult hoveredPoint;
    private float renderedPartialTick = 1;
    private final LerpedFloat renderPitch = LerpedFloat.angular();
    private final LerpedFloat renderYaw = LerpedFloat.angular();
    private final ScreenTooltipBoxWidget resetButton;
    @Nullable private ScreenTooltipBoxWidget previewButton;
    private final AABBOutline pointPickOutline = new AABBOutline(new AABB(0, 0, 0, 1, 1, 1));
    private final AABBOutline pointPickBorderOutline = new AABBOutline(new AABB(0, 0, 0, 1, 1, 1));

    SceneEditorPreview() {
        pointPickOutline.getParams().lineWidth(1 / 48f).colored(0xFFFFFF).disableLineNormals();
        pointPickBorderOutline.getParams().lineWidth(1 / 24f).colored(0x000000).disableLineNormals();
        resetButton = new ScreenTooltipBoxWidget(0, 0, 24, 24);
        resetButton.withPadding(4, 4).withCallback(this::resetView);
        resetButton.setMessage(Component.translatable("ponderer.ui.preview.reset"));
        resetButton.showingElement(PonderGuiTextures.ICON_PONDER_REPLAY.asStencil()
            .withElementRenderer(BoxWidget.gradientFactory.apply(resetButton)));
    }

    void setStepPreviewAction(Runnable action) {
        previewButton = new ScreenTooltipBoxWidget(0, 0, 24, 24);
        previewButton.withPadding(4, 4).withCallback(action);
        previewButton.setMessage(Component.translatable("ponderer.ui.step_editor.preview"));
        previewButton.showingElement(PonderGuiTextures.ICON_PONDER_RIGHT.asStencil()
            .withElementRenderer(BoxWidget.gradientFactory.apply(previewButton)));
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
        zoom = DEFAULT_ZOOM;
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

    boolean load(DslScene definition, int segmentIndex) {
        PointPick pendingPick = pointPick;
        close();
        try {
            DynamicPonderPlugin.EditorScene compiled = new DynamicPonderPlugin().compileEditorScene(definition, segmentIndex);
            if (compiled == null) return false;
            ponder = new PreviewPonderUI(compiled.scene(), compiled.playback());
            ponder.width = width;
            ponder.height = height;
            PreviewPonderUI owner = ponder;
            progressBar = new PonderProgressBar(owner, 0, 0, 1, 1) {
                @Override public void onClick(double mouseX, double mouseY) {
                    // The native seek skips an unchanged tick. Reset paused playback first so
                    // clicking its current position (including the end) still restores later steps.
                    if (owner.playback.isPreviewingStep()) owner.seekToTime(0);
                    super.onClick(mouseX, mouseY);
                }
            };
            stepTicks = List.copyOf(compiled.stepTicks());
            seekStep(0);
            layoutProgressBar();
            pointPick = pendingPick;
            return true;
        } catch (RuntimeException e) {
            close();
            LogUtils.getLogger().warn("Unable to compile scene editor preview for {} segment {}", definition.id, segmentIndex, e);
            return false;
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
        if (previewButton != null) {
            previewButton.setX(resetButton.getX() - previewButton.getWidth() - 4);
            previewButton.setY(resetButton.getY());
        }
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
        if (playingStep && ponder != null) {
            PonderScene scene = ponder.getActiveScene();
            ProjectorRenderContext.runWithFrameTime(scene.getCurrentTime(), 0, scene::tick);
            listSeekStart = Util.getMillis();
            frozenPartialTick = 1;
            if (ponder.playback.isStepComplete() || scene.isFinished()) {
                playingStep = false;
            }
        }
        if (progressBar != null) progressBar.tick();
        resetButton.tick();
        if (previewButton != null) previewButton.tick();
    }

    void playStep(int index) {
        if (ponder == null || index < 0 || index >= stepTicks.size()) return;
        cancelPointPick();
        pendingListTick = -1;
        scrubbing = false;
        dragButton = -1;
        ponder.seekToTime(Math.max(0, stepTicks.get(index) - 1));
        ponder.playback.previewStep(index);
        listSeekStart = -1;
        frozenPartialTick = 1;
        playingStep = true;
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
        playingStep = false;
        if (!scrubbing && index >= 0 && index < stepTicks.size()) {
            // Scroll events in the same frame replace one pending seek; never queue replays.
            pendingListTick = stepTicks.get(index);
        }
    }

    boolean isScrubbing() { return scrubbing; }

    boolean startPointPick(PointPickMode mode, Consumer<Vec3> onPicked) {
        if (ponder == null) return false;
        cancelPointPick();
        playingStep = false;
        pointPick = new PointPick(mode, onPicked);
        dragButton = -1;
        return true;
    }

    boolean cancelPointPick() {
        if (pointPick == null) return false;
        pointPick = null;
        hoveredPoint = null;
        if (ponder != null) ponder.getActiveScene().deselect();
        return true;
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (ponder == null) {
            graphics.drawCenteredString(Minecraft.getInstance().font,
                Component.translatable("ponderer.ui.scene_editor.preview_unavailable"),
                x + width / 2, y + height / 2, 0xAAAAAA);
            renderButtons(graphics, mouseX, mouseY, partialTicks);
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
        renderedPartialTick = scenePartialTick;
        hoveredPoint = findPoint(mouseX, mouseY);
        renderScene(graphics, scenePartialTick);
        // Use the native widget unchanged, including its keyframe markers and drag/snap mixin.
        progressBar.render(graphics, mouseX, mouseY, partialTicks);
        renderButtons(graphics, mouseX, mouseY, partialTicks);
    }

    private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0, 0, PonderRuntimeZLayers.PONDER_BUTTON_LAYER + 500);
            resetButton.render(graphics, mouseX, mouseY, partialTicks);
            if (previewButton != null) previewButton.render(graphics, mouseX, mouseY, partialTicks);
        } finally {
            graphics.pose().popPose();
        }
    }

    void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (previewButton != null && previewButton.isMouseOver(mouseX, mouseY)) {
            renderControlTooltip(graphics, mouseX, mouseY,
                List.of(Component.translatable("ponderer.ui.step_editor.preview")));
            return;
        }
        if (ponder == null) return;
        if (pointPick != null && containsModel(mouseX, mouseY) && !isOverControls(mouseX, mouseY)) {
            var lines = new java.util.ArrayList<Component>();
            lines.add(Component.translatable("ponderer.ui.pick.preview.hint"));
            if (hoveredPoint != null) {
                Vec3 point = hoveredPoint.point();
                lines.add(Component.literal(String.format(Locale.ROOT, "[ %s, %s, %s ]",
                    formatCoordinate(point.x), formatCoordinate(point.y), formatCoordinate(point.z))));
            }
            graphics.flush();
            graphics.pose().pushPose();
            try {
                RenderSystem.disableDepthTest();
                graphics.pose().translate(0, 0, PonderRuntimeZLayers.PONDER_BUTTON_LAYER + 800);
                graphics.renderComponentTooltip(Minecraft.getInstance().font, lines, mouseX, mouseY);
                graphics.flush();
            } finally {
                graphics.pose().popPose();
            }
            return;
        }
        if (!resetButton.isMouseOver(mouseX, mouseY)) return;
        renderControlTooltip(graphics, mouseX, mouseY,
            PonderFreePreview.controlsTooltip("ponderer.ui.preview.reset", textVisible));
    }

    private void renderControlTooltip(GuiGraphics graphics, int mouseX, int mouseY, List<Component> lines) {
        graphics.flush();
        graphics.pose().pushPose();
        try {
            RenderSystem.disableDepthTest();
            graphics.pose().translate(0, 0, PonderRuntimeZLayers.PONDER_BUTTON_LAYER + 800);
            graphics.renderComponentTooltip(Minecraft.getInstance().font, lines, mouseX, mouseY);
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
            // Temporarily share the native transform with both the structure and text anchors.
            // Restore the scripted chasers and scale afterward so seeking stays deterministic.
            applyPreviewTransform(scene, partialTick);
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
                renderPointPick(graphics, partialTick);
                if (pointPick != null) renderEditingCoordinates(graphics, scene);
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
        if (previewButton != null && previewButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (ponder == null) return false;
        if (pointPick != null && containsModel(mouseX, mouseY) && !isOverControls(mouseX, mouseY)
            && button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            // Recalculate against the rendered camera, without relying on the last hovered block.
            PointPickResult result = findPoint(mouseX, mouseY);
            if (result != null) {
                Consumer<Vec3> onPicked = pointPick.onPicked();
                cancelPointPick();
                onPicked.accept(result.point());
            }
            return true;
        }
        if (resetButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (progressBar.mouseClicked(mouseX, mouseY, button)) {
            playingStep = false;
            pendingListTick = -1;
            scrubbing = true;
            listSeekStart = -1;
            frozenPartialTick = 0;
            progressBar.setFocused(true);
            return true;
        }
        if (containsModel(mouseX, mouseY) && !isOverControls(mouseX, mouseY) && (button == 0 || button == 1)) {
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

    private boolean isOverControls(double mouseX, double mouseY) {
        return resetButton.isMouseOver(mouseX, mouseY)
            || previewButton != null && previewButton.isMouseOver(mouseX, mouseY);
    }

    private void applyPreviewTransform(PonderScene scene, float partialTick) {
        var transform = scene.getTransform();
        var view = (PonderSceneViewOffsetAccess) scene;
        float scale = view.ponderer$isScaleOverrideActive()
            ? view.ponderer$getScaleOverride().getValue(partialTick) : scene.getScaleFactor();
        var bounds = scene.getBounds();
        double fit = Math.min(width, height) * .68 / Math.max(1,
            Math.max(bounds.getYSpan(), Math.max(bounds.getXSpan(), bounds.getZSpan())));
        transform.xRotation = renderPitch.startWithValue(Mth.clamp(transform.xRotation.getValue(partialTick) + pitch, -89, 89));
        transform.yRotation = renderYaw.startWithValue(transform.yRotation.getValue(partialTick) + yaw);
        view.ponderer$setScaleOverrideActive(false);
        ((PonderSceneAccessor) scene).ponderer$setScaleFactor((float) fit * zoom * scale / 30);
        transform.updateScreenParams(width + (int) Math.round(panX * 2), height + (int) Math.round(panY * 2), 0);
    }

    @Nullable
    private PointPickResult findPoint(double mouseX, double mouseY) {
        if (pointPick == null || ponder == null) return null;
        PonderScene scene = ponder.getActiveScene();
        scene.deselect();
        if (!containsModel(mouseX, mouseY) || isOverControls(mouseX, mouseY)) return null;

        var transform = scene.getTransform();
        var originalPitch = transform.xRotation;
        var originalYaw = transform.yRotation;
        float originalScale = scene.getScaleFactor();
        var view = (PonderSceneViewOffsetAccess) scene;
        boolean scaleOverride = view.ponderer$isScaleOverrideActive();
        PointPickResult[] result = {null};
        try {
            applyPreviewTransform(scene, renderedPartialTick);
            PoseStack projection = new PoseStack();
            transform.apply(projection, renderedPartialTick);
            // Invert the actual rendering matrix, including scripted view offsets and the free camera.
            Matrix4f inverse = new Matrix4f(projection.last().pose()).invert();
            ProjectorRenderContext.runWithFrameTime(scene.getCurrentTime(), renderedPartialTick, () -> {
                SectionHit[] nearest = {null};
                var bounds = scene.getWorld().getBounds();
                scene.forEach(WorldSectionElement.class, section -> {
                    if (!section.isVisible()) return;
                    Matrix4f sectionProjection;
                    projection.pushPose();
                    try {
                        if (section instanceof WorldSectionElementImpl animated) {
                            animated.transformMS(projection, renderedPartialTick);
                        }
                        sectionProjection = new Matrix4f(projection.last().pose());
                    } finally {
                        projection.popPose();
                    }
                    // Fit the ray to this section's projected bounds at any zoom or animation offset.
                    Vector3f center = sectionProjection.transformPosition(new Vector3f(
                        bounds.minX() + bounds.getXSpan() * .5f,
                        bounds.minY() + bounds.getYSpan() * .5f,
                        bounds.minZ() + bounds.getZSpan() * .5f));
                    float radius = (Math.abs(sectionProjection.m02()) * bounds.getXSpan()
                        + Math.abs(sectionProjection.m12()) * bounds.getYSpan()
                        + Math.abs(sectionProjection.m22()) * bounds.getZSpan()) * .5f + 1;
                    Vec3 from = unproject(inverse, mouseX - x, mouseY - y, center.z + radius);
                    Vec3 to = unproject(inverse, mouseX - x, mouseY - y, center.z - radius);
                    var trace = section.rayTrace(scene.getWorld(), from, to);
                    if (trace == null || trace.getSecond().getType() != HitResult.Type.BLOCK) return;
                    BlockHitResult hit = trace.getSecond();
                    if (!bounds.isInside(hit.getBlockPos())) return;
                    Vec3 location = hit.getLocation();
                    float depth = sectionProjection.transformPosition(new Vector3f(
                        (float) location.x, (float) location.y, (float) location.z)).z;
                    if (nearest[0] == null || depth > nearest[0].depth()) {
                        nearest[0] = new SectionHit(section, hit, depth);
                    }
                });
                if (nearest[0] == null) return;
                SectionHit selected = nearest[0];
                var pos = selected.hit().getBlockPos();
                Direction face = selected.hit().getDirection();
                Vec3 point = switch (pointPick.mode()) {
                    case FACE_CENTER -> Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5));
                    case ADJACENT_BLOCK -> Vec3.atLowerCornerOf(pos.relative(face));
                    case BLOCK -> Vec3.atLowerCornerOf(pos);
                };
                result[0] = new PointPickResult(point, face, selected.section());
            });
        } finally {
            transform.xRotation = originalPitch;
            transform.yRotation = originalYaw;
            ((PonderSceneAccessor) scene).ponderer$setScaleFactor(originalScale);
            view.ponderer$setScaleOverrideActive(scaleOverride);
            transform.updateScreenParams(width, height, 0);
        }
        return result[0];
    }

    private static Vec3 unproject(Matrix4f inverse, double x, double y, float depth) {
        Vector3f point = inverse.transformPosition(new Vector3f((float) x, (float) y, depth));
        return new Vec3(point.x, point.y, point.z);
    }

    private void renderPointPick(GuiGraphics graphics, float partialTick) {
        if (pointPick == null || hoveredPoint == null) return;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            if (hoveredPoint.section() instanceof WorldSectionElementImpl section) {
                section.transformMS(pose, partialTick);
            }
            RenderSystem.enableDepthTest();
            Vec3 point = hoveredPoint.point();
            if (pointPick.mode() == PointPickMode.FACE_CENTER) {
                // Keep both arms on the selected face, with a small offset to avoid z-fighting.
                Vec3 normal = Vec3.atLowerCornerOf(hoveredPoint.face().getNormal());
                Vec3 center = point.add(normal.scale(1 / 256f));
                pose.translate(center.x, center.y, center.z);
                pose.mulPose(hoveredPoint.face().getRotation());
                renderFaceCross(pose.last().pose());
            } else {
                // The picked integer is the lower corner of the target block (adjacent for placement).
                AABB bounds = new AABB(point, point.add(1, 1, 1));
                pointPickOutline.setBounds(bounds);
                pointPickBorderOutline.setBounds(bounds);
                var buffer = DefaultSuperRenderTypeBuffer.getInstance();
                // Keep the wider black edges out of the depth buffer so they cannot hide the white core.
                RenderSystem.depthMask(false);
                try {
                    pointPickBorderOutline.render(pose, buffer, Vec3.ZERO, partialTick);
                    buffer.draw();
                } finally {
                    RenderSystem.depthMask(true);
                }
                pointPickOutline.render(pose, buffer, Vec3.ZERO, partialTick);
                buffer.draw();
            }
        } finally {
            pose.popPose();
        }
    }

    private static void renderFaceCross(Matrix4f matrix) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            float halfLength = .25f;
            float halfWidth = .02f;
            float borderWidth = .02f;
            float outerLength = halfLength + borderWidth;
            float outerWidth = halfWidth + borderWidth;
            int borderColor = 0xCC000000;
            // Tile the border around the white cross instead of layering coplanar crosses.
            // Every fragment is drawn once, including the four inner corners.
            faceQuad(buffer, matrix, -outerWidth, -outerLength, outerWidth, -halfLength, borderColor);
            faceQuad(buffer, matrix, -outerWidth, halfLength, outerWidth, outerLength, borderColor);
            faceQuad(buffer, matrix, -outerLength, -outerWidth, -halfLength, outerWidth, borderColor);
            faceQuad(buffer, matrix, halfLength, -outerWidth, outerLength, outerWidth, borderColor);
            faceQuad(buffer, matrix, -outerWidth, -halfLength, -halfWidth, -halfWidth, borderColor);
            faceQuad(buffer, matrix, halfWidth, -halfLength, outerWidth, -halfWidth, borderColor);
            faceQuad(buffer, matrix, -outerWidth, halfWidth, -halfWidth, halfLength, borderColor);
            faceQuad(buffer, matrix, halfWidth, halfWidth, outerWidth, halfLength, borderColor);
            faceQuad(buffer, matrix, -halfLength, -outerWidth, -outerWidth, -halfWidth, borderColor);
            faceQuad(buffer, matrix, outerWidth, -outerWidth, halfLength, -halfWidth, borderColor);
            faceQuad(buffer, matrix, -halfLength, halfWidth, -outerWidth, outerWidth, borderColor);
            faceQuad(buffer, matrix, outerWidth, halfWidth, halfLength, outerWidth, borderColor);
            faceQuad(buffer, matrix, -halfLength, -halfWidth, halfLength, halfWidth, 0xFFFFFFFF);
            faceQuad(buffer, matrix, -halfWidth, -halfLength, halfWidth, -halfWidth, 0xFFFFFFFF);
            faceQuad(buffer, matrix, -halfWidth, halfWidth, halfWidth, halfLength, 0xFFFFFFFF);
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private static void faceQuad(BufferBuilder buffer, Matrix4f matrix,
                                 float minX, float minZ, float maxX, float maxZ, int color) {
        // Culling is disabled for this face-attached marker; submit each quad only once.
        buffer.addVertex(matrix, minX, 0, minZ).setColor(color);
        buffer.addVertex(matrix, maxX, 0, minZ).setColor(color);
        buffer.addVertex(matrix, maxX, 0, maxZ).setColor(color);
        buffer.addVertex(matrix, minX, 0, maxZ).setColor(color);
    }

    /** Local editor view; never changes Ponder's global editing configuration or localization mode. */
    private void renderEditingCoordinates(GuiGraphics graphics, PonderScene scene) {
        var pose = graphics.pose();
        var font = Minecraft.getInstance().font;
        var bounds = scene.getBounds();
        pose.pushPose();
        try {
            pose.scale(-1 / 16f, -1 / 16f, 1 / 16f);
            pose.translate(1, -8, -1 / 64f);
            pose.pushPose();
            pose.translate(4, -3, 0);
            for (int i = 0; i <= bounds.getXSpan(); i++) {
                pose.translate(-16, 0, 0);
                graphics.drawString(font, i == bounds.getXSpan() ? "x" : Integer.toString(i), 0, 0, 0xFFFFFFFF, false);
            }
            pose.popPose();
            pose.scale(-1, 1, 1);
            pose.translate(0, -3, -4);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-90));
            pose.translate(-8, -2, 2 / 64f);
            for (int i = 0; i <= bounds.getZSpan(); i++) {
                pose.translate(16, 0, 0);
                graphics.drawString(font, i == bounds.getZSpan() ? "z" : Integer.toString(i), 0, 0, 0xFFFFFFFF, false);
            }
        } finally {
            pose.popPose();
        }
    }

    private static String formatCoordinate(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value)) : Double.toString(value);
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
        cancelPointPick();
        if (ponder != null) PonderRenderCache.releaseDiscardedScene(ponder.getActiveScene());
        ponder = null;
        progressBar = null;
        stepTicks = List.of();
        playingStep = false;
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
        private final DynamicPonderPlugin.EditorScenePlayback playback;

        PreviewPonderUI(PonderScene scene, DynamicPonderPlugin.EditorScenePlayback playback) {
            super(List.of(scene));
            initialScale = scene.getScaleFactor();
            this.playback = playback;
        }

        @Override public Font getFontRenderer() {
            return Minecraft.getInstance().font;
        }

        @Override public void seekToTime(int time) {
            int target = Mth.clamp(time, 0, getActiveScene().getTotalTime());
            if (playback.isPreviewingStep()) {
                // Preview ticks leave later instructions paused. Rebuild their normal state even
                // for a forward seek, so the full timeline remains accurate after previewing.
                playback.resumeTimeline();
                ProjectorRenderContext.run(() -> {
                    replay();
                    super.seekToTime(target);
                });
            } else if (target != getActiveScene().getCurrentTime()) {
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
