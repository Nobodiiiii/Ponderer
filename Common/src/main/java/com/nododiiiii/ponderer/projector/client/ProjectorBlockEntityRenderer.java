package com.nododiiiii.ponderer.projector.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.mixin.InputWindowElementAccessor;
import com.nododiiiii.ponderer.mixin.RenderSystemShaderLightsAccessor;
import com.nododiiiii.ponderer.mixin.TextWindowElementAccessor;
import com.nododiiiii.ponderer.projector.ProjectorBlock;
import com.nododiiiii.ponderer.projector.ProjectorBlockEntity;
import com.nododiiiii.ponderer.projector.ProjectorKind;
import com.nododiiiii.ponderer.projector.ProjectorProjectionMode;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.element.ScreenElement;
import net.createmod.catnip.impl.client.render.ColoringVertexConsumer;
import net.createmod.catnip.math.Pointing;
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.AnimatedOverlayElement;
import net.createmod.ponder.api.element.PonderElement;
import net.createmod.ponder.api.element.PonderOverlayElement;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.InputWindowElement;
import net.createmod.ponder.foundation.element.TextWindowElement;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class ProjectorBlockEntityRenderer implements BlockEntityRenderer<ProjectorBlockEntity> {

    private static final float MINIATURE_FILL = 0.85F;
    private static final float MINIATURE_Y_OFFSET = 1.06F;
    private static final float MINIATURE_BEAM_SOURCE_Y = 14.0F / 16.0F;
    private static final float MINIATURE_BEAM_OUTER_HEIGHT = 0.05F;
    private static final float MINIATURE_BEAM_BASE_RADIUS = 0.06F;
    private static final float LIFE_SIZE_BEAM_FACE_WIDTH = 8.5F / 16.0F;
    private static final float LIFE_SIZE_BEAM_SOURCE_Y = 9.5F / 16.0F;
    private static final float LIFE_SIZE_BEAM_FACE_INSET = 2.0F / 16.0F;
    private static final float LIFE_SIZE_BEAM_OUTER_LENGTH = 0.05F;
    private static final float LIFE_SIZE_BEAM_BASE_HALF_EXTENT = 0.5F;
    private static final float LIFE_SIZE_CARD_RISE = 0.85F;
    /** Local billboard z step in font-pixel units. The pose is scaled to world space later, so half a
     *  local pixel is enough to break depth ties without making the projected UI visibly float. */
    private static final float LOCAL_OVERLAY_Z_STEP = 0.5F;
    /** Depth-tested overlays still need an internal layer stack. Native Ponder's TextWindow puts the
     *  box, guide line, and text on distinct GUI z levels; mirror that ordering in billboard-local space. */
    private static final float LOCAL_OVERLAY_BACKGROUND_Z = LOCAL_OVERLAY_Z_STEP;
    private static final float LOCAL_OVERLAY_LEADER_Z = LOCAL_OVERLAY_Z_STEP * 2.0F;
    private static final float LOCAL_OVERLAY_FOREGROUND_Z = LOCAL_OVERLAY_Z_STEP * 3.0F;
    /** Global scale multiplier for all projected overlay UI (text windows, panels, cards).
     *  Base scale is 1.0; default 2.0 doubles the size of text, boxes, and line width.
     *  所有投影叠加层 UI（文本窗口、面板、卡片）的全局缩放倍数。
     *  基础缩放为 1.0；默认 2.0 将文本、框体和线宽的大小翻倍。*/
    private static final float OVERLAY_UI_SCALE = 1.0F;
    /** Horizontal leader length (in billboard-local font pixels) between the anchor point and the left edge of an
     *  anchored text card. The card text and box are drawn to the screen-right of the anchor by this much, with a
     *  thin horizontal guide line bridging the gap — mirroring Ponder's {@code TextWindowElement} layout. */
    private static final float OVERLAY_LEADER_LENGTH = 18.0F;
    /** Leader line thickness in billboard-local font pixels, so it scales with the same perspective and overlay
     *  size changes as the text box instead of staying a fixed screen-space line width. */
    private static final float OVERLAY_LEADER_THICKNESS = 1.0F;
    /** Squash factor applied to the item's depth (toward camera). A full 3D item is as deep as it is tall, which
     *  parallaxes against the flat box; squashing it emulates the orthographic inventory look (1 = full 3D, 0 = flat).
     *  Only geometry is squashed; {@link #drawPanelItem} repairs the normal matrix afterward so diffuse lighting
     *  still matches a real inventory item (a 0 z-scale would make the normal matrix singular and render it dark).
     *  Set to a small NEGATIVE value to give multi-layer block entities (chests) enough depth separation for correct
     *  layer ordering without visible parallax, and flip the z-axis so Minecraft's back-to-front model geometry
     *  renders front-to-back on screen (chest lid on top). */
    private static final float PANEL_ITEM_FLATTEN = -0.05F;
    /** Far edge of the compressed depth-range window the panel item renders into. The item's geometry is
     *  remapped into [0, this] at the front of the depth buffer so it always wins the depth test against the
     *  projected ponder scene (never occluded), while its own layers still sort among each other inside the
     *  window. Small enough to stay ahead of the scene, wide enough to avoid layer z-fighting. */
    private static final double PANEL_ITEM_DEPTH_FRONT = 0.05D;
    private static TextureTarget preservedWorldDepthTarget;
    private static int preservedWorldDepthWidth = -1;
    private static int preservedWorldDepthHeight = -1;
    private static boolean preservedWorldDepthCapturedThisFrame;
    private static final Set<Long> RENDERED_THIS_FRAME = new HashSet<>();

    /**
     * Private, isolated buffer for the show_controls panel. Never the shared world buffer source, so
     * flushing it mid-frame cannot disturb any other world rendering.
     */
    private final MultiBufferSource.BufferSource panelBuffer =
        MultiBufferSource.immediate(new BufferBuilder(256));
    private final Map<RenderType, RenderType> panelNoDepthRenderTypes = new IdentityHashMap<>();
    private final MultiBufferSource panelNoDepthBuffer =
        type -> panelBuffer.getBuffer(panelNoDepthRenderType(type));

    public ProjectorBlockEntityRenderer() {
    }

    public ProjectorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    static void beginFrame() {
        preservedWorldDepthCapturedThisFrame = false;
        RENDERED_THIS_FRAME.clear();
    }

    @Override
    public void render(ProjectorBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        if (!RENDERED_THIS_FRAME.add(blockEntity.getBlockPos().asLong())) {
            return;
        }

        double distanceSqr = distanceToRenderBoundsSqr(blockEntity);
        if (distanceSqr > ProjectorRenderDistances.PROJECTION_RENDER_DISTANCE_SQR) {
            return;
        }

        ProjectorPlaybackState.PreparedFrame prepared = ProjectorPlaybackState.prepareForRender(blockEntity, partialTick);
        if (prepared == null) {
            return;
        }

        BoundingBox layoutBounds = blockEntity.getProjectorKind() == ProjectorKind.MINIATURE
            ? prepared.segment().scene().getBounds()
            : prepared.bundle().combinedBounds();
        RenderLayout layout = RenderLayout.from(blockEntity, layoutBounds,
            prepared.activeScene(), partialTick);
        boolean antiOcclusion = blockEntity.overlayAntiOcclusion();
        ProjectorProjectionMode projectionMode = blockEntity.getProjectorKind().requiresAnchor()
            ? blockEntity.getProjectionMode()
            : ProjectorProjectionMode.DEFAULT;

        boolean shouldRenderText = projectionMode.rendersText()
            && distanceSqr <= ProjectorRenderDistances.OVERLAY_RENDER_DISTANCE_SQR;
        DeferredOverlayBatch combinedOverlays = DeferredOverlayBatch.empty();
        if (shouldRenderText) {
            PoseSnapshot overlayBasePose = PoseSnapshot.capture(poseStack);
            DeferredOverlayBatch deferredNativeOverlay = captureNativePonderOverlays(prepared.activeScene(), layout,
                partialTick, overlayBasePose, antiOcclusion);
            DeferredOverlayBatch deferredCueOverlay = DeferredOverlayBatch.empty();
            if (!prepared.segment().extractRuntimeOverlays() || deferredNativeOverlay.isEmpty()) {
                List<ProjectorSceneBundle.OverlayCue> cues = prepared.bundle()
                    .activeCues(prepared.segment(), prepared.localTick(), partialTick,
                        blockEntity.compatibilityMode());
                deferredCueOverlay = captureOverlayCues(cues, layout, overlayBasePose, antiOcclusion);
            }
            combinedOverlays = DeferredOverlayBatch.combine(deferredNativeOverlay, deferredCueOverlay);
        }

        if (!antiOcclusion && !combinedOverlays.isEmpty()) {
            captureWorldDepthIfNeeded();
        }
        enqueueProjectionGlow(blockEntity, layout, poseStack, partialTick);
        if (projectionMode.rendersScene()) {
            renderProjectedScene(prepared.activeScene(), layout, poseStack, prepared.localTick(), partialTick);
        }

        if (!combinedOverlays.isEmpty()) {
            ProjectorWorldOverlayQueue.enqueue(this, combinedOverlays);
        }
    }

    private void renderProjectedScene(PonderScene scene, RenderLayout layout, PoseStack poseStack,
                                      int localTick, float partialTick) {
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(layout.redTint(), layout.greenTint(), layout.blueTint(), 1.0F);

        poseStack.pushPose();
        poseStack.translate(layout.origin().x, layout.origin().y, layout.origin().z);
        poseStack.translate(layout.rotationPivot().x, layout.rotationPivot().y, layout.rotationPivot().z);
        poseStack.mulPose(Axis.YP.rotationDegrees(layout.rotationDegrees()));
        poseStack.mulPose(Axis.YP.rotationDegrees(layout.sceneRotation().yDegrees()));
        poseStack.scale(layout.scale(), layout.scale(), layout.scale());
        poseStack.translate(layout.sceneTranslate().x, layout.sceneTranslate().y, layout.sceneTranslate().z);

        GuiGraphics graphics = ProjectorGuiGraphicsBridge.create(poseStack);
        DefaultSuperRenderTypeBuffer sceneBuffer = DefaultSuperRenderTypeBuffer.getInstance();
        SuperRenderTypeBuffer projectedBuffer = new ProjectedRenderTypeBuffer(
            sceneBuffer,
            layout.redTint(),
            layout.greenTint(),
            layout.blueTint());
        ProjectorRenderContext.runWithFrameTime(localTick, partialTick,
            () -> scene.renderScene(projectedBuffer, graphics, partialTick));
        sceneBuffer.draw();

        poseStack.popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void captureWorldDepthIfNeeded() {
        if (preservedWorldDepthCapturedThisFrame) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        TextureTarget preservedDepth = ensurePreservedWorldDepthTarget(minecraft);
        int currentFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        copyDepth(currentFramebuffer, preservedDepth.frameBufferId,
            preservedDepth.width, preservedDepth.height);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, currentFramebuffer);
        preservedWorldDepthCapturedThisFrame = true;
    }

    private static void restorePreservedWorldDepth() {
        if (!preservedWorldDepthCapturedThisFrame) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        TextureTarget preservedDepth = ensurePreservedWorldDepthTarget(minecraft);
        int currentFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        copyDepth(preservedDepth.frameBufferId, currentFramebuffer,
            preservedDepth.width, preservedDepth.height);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, currentFramebuffer);
    }

    private static void copyDepth(int srcFramebuffer, int dstFramebuffer, int width, int height) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFramebuffer);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dstFramebuffer);
        GlStateManager._glBlitFrameBuffer(
            0, 0, width, height,
            0, 0, width, height,
            GL11.GL_DEPTH_BUFFER_BIT,
            GL11.GL_NEAREST);
    }

    private static TextureTarget ensurePreservedWorldDepthTarget(Minecraft minecraft) {
        Window window = minecraft.getWindow();
        int width = window.getWidth();
        int height = window.getHeight();
        if (preservedWorldDepthTarget == null) {
            preservedWorldDepthTarget = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            preservedWorldDepthWidth = width;
            preservedWorldDepthHeight = height;
            return preservedWorldDepthTarget;
        }
        if (preservedWorldDepthWidth != width || preservedWorldDepthHeight != height) {
            preservedWorldDepthTarget.resize(width, height, Minecraft.ON_OSX);
            preservedWorldDepthWidth = width;
            preservedWorldDepthHeight = height;
        }
        return preservedWorldDepthTarget;
    }

    private void enqueueProjectionGlow(ProjectorBlockEntity blockEntity, RenderLayout layout,
                                       PoseStack poseStack, float partialTick) {
        if (!blockEntity.getBlockState().getValue(ProjectorBlock.LIT)
            || !blockEntity.showBlueTint()) {
            return;
        }
        // Life-size projector glow code is kept below, but intentionally not wired into the
        // render queue for now; only miniature projectors currently show the projection beam.
        if (layout.kind() != ProjectorKind.MINIATURE) {
            return;
        }

        ProjectorWorldOverlayQueue.enqueueProjectionGlow(this, new DeferredProjectionGlow(
            PoseSnapshot.capture(poseStack),
            layout,
            blockEntity.getBlockState().getValue(ProjectorBlock.FACING),
            partialTick));
    }

    void renderDeferredProjectionGlow(DeferredProjectionGlow glow) {
        if (glow == null || Minecraft.getInstance().level == null) {
            return;
        }

        float time = Minecraft.getInstance().level.getGameTime() + glow.partialTick();
        float breathe = 0.5F + 0.5F * (float) Math.cos(time * 0.11F);
        float outerScale = 0.88F + 0.12F * breathe;
        ProjectionGlowColors colors = projectionGlowColors(glow.layout(), breathe);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        PoseStack poseStack = glow.poseSnapshot().createPoseStack();
        Matrix4f matrix = poseStack.last().pose();
        if (glow.layout().kind() == ProjectorKind.MINIATURE) {
            renderMiniatureProjectionBeam(matrix, glow.layout(), outerScale, colors);
        } else {
            renderLifeSizeProjectionBeam(matrix, glow.facing(), glow.layout(), outerScale, colors);
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
    }

    private void renderMiniatureProjectionBeam(Matrix4f matrix, RenderLayout layout, float outerScale,
                                               ProjectionGlowColors colors) {
        int spanX = Math.max(1, layout.bounds().getXSpan());
        int spanZ = Math.max(1, layout.bounds().getZSpan());
        float waistY = (float) layout.origin().y;
        float outerSourceY = MINIATURE_BEAM_SOURCE_Y;
        float beamWaistHalfX = layout.scale() * spanX * 0.5F;
        float beamWaistHalfZ = layout.scale() * spanZ * 0.5F;
        float outerHeight = MINIATURE_BEAM_OUTER_HEIGHT * outerScale;
        float outerTopY = waistY + outerHeight;
        float beamTopHalfX = continueBeamHalfExtent(0.0F, beamWaistHalfX, outerSourceY, waistY, outerTopY);
        float beamTopHalfZ = continueBeamHalfExtent(0.0F, beamWaistHalfZ, outerSourceY, waistY, outerTopY);

        Vec3 apex = new Vec3(layout.origin().x, outerSourceY, layout.origin().z);
        Vec3 northWaistLeft = miniatureBeamPoint(layout, -beamWaistHalfX, waistY, -beamWaistHalfZ);
        Vec3 northWaistRight = miniatureBeamPoint(layout, beamWaistHalfX, waistY, -beamWaistHalfZ);
        Vec3 northTopLeft = miniatureBeamPoint(layout, -beamTopHalfX, outerTopY, -beamTopHalfZ);
        Vec3 northTopRight = miniatureBeamPoint(layout, beamTopHalfX, outerTopY, -beamTopHalfZ);
        Vec3 eastWaistLeft = miniatureBeamPoint(layout, beamWaistHalfX, waistY, -beamWaistHalfZ);
        Vec3 eastWaistRight = miniatureBeamPoint(layout, beamWaistHalfX, waistY, beamWaistHalfZ);
        Vec3 eastTopLeft = miniatureBeamPoint(layout, beamTopHalfX, outerTopY, -beamTopHalfZ);
        Vec3 eastTopRight = miniatureBeamPoint(layout, beamTopHalfX, outerTopY, beamTopHalfZ);
        Vec3 southWaistLeft = miniatureBeamPoint(layout, beamWaistHalfX, waistY, beamWaistHalfZ);
        Vec3 southWaistRight = miniatureBeamPoint(layout, -beamWaistHalfX, waistY, beamWaistHalfZ);
        Vec3 southTopLeft = miniatureBeamPoint(layout, beamTopHalfX, outerTopY, beamTopHalfZ);
        Vec3 southTopRight = miniatureBeamPoint(layout, -beamTopHalfX, outerTopY, beamTopHalfZ);
        Vec3 westWaistLeft = miniatureBeamPoint(layout, -beamWaistHalfX, waistY, beamWaistHalfZ);
        Vec3 westWaistRight = miniatureBeamPoint(layout, -beamWaistHalfX, waistY, -beamWaistHalfZ);
        Vec3 westTopLeft = miniatureBeamPoint(layout, -beamTopHalfX, outerTopY, beamTopHalfZ);
        Vec3 westTopRight = miniatureBeamPoint(layout, -beamTopHalfX, outerTopY, -beamTopHalfZ);

        renderBeamTriangle(
            matrix,
            (float) apex.x, (float) apex.y, (float) apex.z,
            (float) northWaistLeft.x, (float) northWaistLeft.y, (float) northWaistLeft.z,
            (float) northWaistRight.x, (float) northWaistRight.y, (float) northWaistRight.z,
            colors.sourceColor(),
            colors.waistColor());
        renderBeamTrapezoid(
            matrix,
            (float) northWaistLeft.x, (float) northWaistLeft.y, (float) northWaistLeft.z,
            (float) northWaistRight.x, (float) northWaistRight.y, (float) northWaistRight.z,
            (float) northTopRight.x, (float) northTopRight.y, (float) northTopRight.z,
            (float) northTopLeft.x, (float) northTopLeft.y, (float) northTopLeft.z,
            colors.waistColor(),
            colors.topColor());
        renderBeamTriangle(
            matrix,
            (float) apex.x, (float) apex.y, (float) apex.z,
            (float) eastWaistLeft.x, (float) eastWaistLeft.y, (float) eastWaistLeft.z,
            (float) eastWaistRight.x, (float) eastWaistRight.y, (float) eastWaistRight.z,
            colors.sourceColor(),
            colors.waistColor());
        renderBeamTrapezoid(
            matrix,
            (float) eastWaistLeft.x, (float) eastWaistLeft.y, (float) eastWaistLeft.z,
            (float) eastWaistRight.x, (float) eastWaistRight.y, (float) eastWaistRight.z,
            (float) eastTopRight.x, (float) eastTopRight.y, (float) eastTopRight.z,
            (float) eastTopLeft.x, (float) eastTopLeft.y, (float) eastTopLeft.z,
            colors.waistColor(),
            colors.topColor());
        renderBeamTriangle(
            matrix,
            (float) apex.x, (float) apex.y, (float) apex.z,
            (float) southWaistLeft.x, (float) southWaistLeft.y, (float) southWaistLeft.z,
            (float) southWaistRight.x, (float) southWaistRight.y, (float) southWaistRight.z,
            colors.sourceColor(),
            colors.waistColor());
        renderBeamTrapezoid(
            matrix,
            (float) southWaistLeft.x, (float) southWaistLeft.y, (float) southWaistLeft.z,
            (float) southWaistRight.x, (float) southWaistRight.y, (float) southWaistRight.z,
            (float) southTopRight.x, (float) southTopRight.y, (float) southTopRight.z,
            (float) southTopLeft.x, (float) southTopLeft.y, (float) southTopLeft.z,
            colors.waistColor(),
            colors.topColor());
        renderBeamTriangle(
            matrix,
            (float) apex.x, (float) apex.y, (float) apex.z,
            (float) westWaistLeft.x, (float) westWaistLeft.y, (float) westWaistLeft.z,
            (float) westWaistRight.x, (float) westWaistRight.y, (float) westWaistRight.z,
            colors.sourceColor(),
            colors.waistColor());
        renderBeamTrapezoid(
            matrix,
            (float) westWaistLeft.x, (float) westWaistLeft.y, (float) westWaistLeft.z,
            (float) westWaistRight.x, (float) westWaistRight.y, (float) westWaistRight.z,
            (float) westTopRight.x, (float) westTopRight.y, (float) westTopRight.z,
            (float) westTopLeft.x, (float) westTopLeft.y, (float) westTopLeft.z,
            colors.waistColor(),
            colors.topColor());
    }

    private void renderLifeSizeProjectionBeam(Matrix4f matrix, Direction facing,
                                              RenderLayout layout, float outerScale,
                                              ProjectionGlowColors colors) {
        Vec3 source = new Vec3(
            lifeSizeBeamSourceX(facing),
            LIFE_SIZE_BEAM_SOURCE_Y,
            lifeSizeBeamSourceZ(facing));
        Vec3 baseCenter = layout.localPointFor(new Vec3(0.5D, 0.5D, 0.5D));
        Vec3 axis = baseCenter.subtract(source);
        if (axis.lengthSqr() < 1.0E-6D) {
            axis = new Vec3(facing.getStepX(), 0.0D, facing.getStepZ());
        }

        Vec3 axisNormal = axis.normalize();
        Direction sideDirection = facing.getClockWise();
        Vec3 side = new Vec3(sideDirection.getStepX(), 0.0D, sideDirection.getStepZ());
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);

        float baseDistance = (float) axis.length();
        float outerLength = LIFE_SIZE_BEAM_OUTER_LENGTH * outerScale;
        float outerHalfExtent = continueBeamHalfExtent(
            0.0F,
            LIFE_SIZE_BEAM_BASE_HALF_EXTENT,
            0.0F,
            baseDistance,
            baseDistance + outerLength);
        Vec3 outerCenter = baseCenter.add(axisNormal.scale(outerLength));

        Vec3 baseTopLeft = beamCorner(baseCenter, side, up, -LIFE_SIZE_BEAM_BASE_HALF_EXTENT,
            LIFE_SIZE_BEAM_BASE_HALF_EXTENT);
        Vec3 baseTopRight = beamCorner(baseCenter, side, up, LIFE_SIZE_BEAM_BASE_HALF_EXTENT,
            LIFE_SIZE_BEAM_BASE_HALF_EXTENT);
        Vec3 baseBottomRight = beamCorner(baseCenter, side, up, LIFE_SIZE_BEAM_BASE_HALF_EXTENT,
            -LIFE_SIZE_BEAM_BASE_HALF_EXTENT);
        Vec3 baseBottomLeft = beamCorner(baseCenter, side, up, -LIFE_SIZE_BEAM_BASE_HALF_EXTENT,
            -LIFE_SIZE_BEAM_BASE_HALF_EXTENT);
        Vec3 outerTopLeft = beamCorner(outerCenter, side, up, -outerHalfExtent, outerHalfExtent);
        Vec3 outerTopRight = beamCorner(outerCenter, side, up, outerHalfExtent, outerHalfExtent);
        Vec3 outerBottomRight = beamCorner(outerCenter, side, up, outerHalfExtent, -outerHalfExtent);
        Vec3 outerBottomLeft = beamCorner(outerCenter, side, up, -outerHalfExtent, -outerHalfExtent);

        renderBeamFace(matrix, source, baseTopLeft, baseTopRight, outerTopRight, outerTopLeft, colors);
        renderBeamFace(matrix, source, baseTopRight, baseBottomRight, outerBottomRight, outerTopRight, colors);
        renderBeamFace(matrix, source, baseBottomRight, baseBottomLeft, outerBottomLeft, outerBottomRight, colors);
        renderBeamFace(matrix, source, baseBottomLeft, baseTopLeft, outerTopLeft, outerBottomLeft, colors);
    }

    private DeferredOverlayBatch captureNativePonderOverlays(PonderScene scene, RenderLayout layout, float partialTick,
                                                             PoseSnapshot overlayBasePose,
                                                             boolean antiOcclusion) {
        Set<PonderElement> elements = scene.getElements();
        if (elements.isEmpty()) {
            return DeferredOverlayBatch.empty();
        }

        List<DeferredOverlay> overlays = new ArrayList<>();
        int fallbackLane = 0;

        for (PonderElement element : elements) {
            if (!(element instanceof PonderOverlayElement) || !element.isVisible()) {
                continue;
            }

            float fade = element instanceof AnimatedOverlayElement animated
                ? animated.getFade(partialTick)
                : 1.0F;
            if (fade < 1.0F / 16.0F) {
                continue;
            }

            try {
                if (element instanceof TextWindowElement textElement) {
                    TextWindowElementAccessor accessor = (TextWindowElementAccessor) textElement;
                    int lane = fallbackLane;
                    if (accessor.ponderer$getVec() == null) {
                        lane = fallbackLaneForY(accessor.ponderer$getY(), fallbackLane);
                    }
                    DeferredOverlay overlay = captureTextOverlay(accessor, fade, lane, layout);
                    if (overlay != null) {
                        overlays.add(overlay);
                        if (accessor.ponderer$getVec() == null) {
                            fallbackLane = lane + 1;
                        }
                    }
                    continue;
                }

                if (element instanceof InputWindowElement inputElement) {
                    DeferredOverlay overlay = captureInputOverlay((InputWindowElementAccessor) inputElement, fade, layout);
                    if (overlay != null) {
                        overlays.add(overlay);
                    }
                }
            } catch (RuntimeException ignored) {
            }
        }

        return DeferredOverlayBatch.of(overlayBasePose, overlays, antiOcclusion);
    }

    private DeferredOverlayBatch captureOverlayCues(List<ProjectorSceneBundle.OverlayCue> cues, RenderLayout layout,
                                                    PoseSnapshot overlayBasePose,
                                                    boolean antiOcclusion) {
        if (cues.isEmpty()) {
            return DeferredOverlayBatch.empty();
        }

        List<DeferredOverlay> overlays = new ArrayList<>(cues.size());
        for (ProjectorSceneBundle.OverlayCue cue : cues) {
            try {
                if (cue.anchorMode() == ProjectorSceneBundle.OverlayCue.AnchorMode.FALLBACK) {
                    overlays.add(DeferredOverlay.billboardCard(
                        layout.fallbackCardPosition(cue.fallbackLane()),
                        cue.lines(),
                        cue.accentColor()));
                    continue;
                }

                Vec3 anchor = layout.localPointFor(cue.point());
                // 卡片锚定在附着点本身，文本框与引导线在 billboard 局部空间内向屏幕右侧偏移。
                // 不能用世界空间 X 偏移：billboard 始终朝向相机，世界 X 不对应"屏幕右侧"，
                // 会随相机角度和投影仪朝向翻转（这正是文本框跑到左边的原因）。
                overlays.add(DeferredOverlay.billboardCardAnchored(anchor, cue.lines(), cue.accentColor()));
            } catch (RuntimeException ignored) {
            }
        }
        return DeferredOverlayBatch.of(overlayBasePose, overlays, antiOcclusion);
    }

    private DeferredOverlay captureTextOverlay(TextWindowElementAccessor accessor, float fade, int fallbackLane,
                                               RenderLayout layout) {
        String text = resolveText(accessor);
        if (text == null || text.isBlank()) {
            return null;
        }

        PonderPalette palette = accessor.ponderer$getPalette();
        Vec3 anchorPoint = accessor.ponderer$getVec();
        if (anchorPoint != null) {
            Vec3 anchor = layout.localPointFor(anchorPoint);
            // 卡片锚定在附着点本身；文本框与引导线在 billboard 局部空间内向屏幕右侧偏移。
            // anchor 同时作为标记，表示需要绘制水平引导线。
            return DeferredOverlay.textWindow(text, anchor, palette, fade, anchor, layout);
        } else {
            Vec3 localPos = layout.fallbackCardPosition(fallbackLane);
            return DeferredOverlay.textWindow(text, localPos, palette, fade, null, layout);
        }
    }

    private DeferredOverlay captureInputOverlay(InputWindowElementAccessor accessor, float fade,
                                                RenderLayout layout) {
        ScreenElement icon = accessor.ponderer$getIcon();
        ResourceLocation key = accessor.ponderer$getKey();
        String text = key == null ? "" : PonderIndex.getLangAccess().getShared(key);
        if (text == null) {
            text = "";
        }

        if (icon == null && text.isBlank() && accessor.ponderer$getItem().isEmpty()) {
            return null;
        }

        return DeferredOverlay.inputBubble(
            accessor.ponderer$getSceneSpace(),
            accessor.ponderer$getDirection(),
            icon,
            text,
            accessor.ponderer$getItem(),
            fade,
            layout);
    }

    void renderDeferredOverlayBatch(DeferredOverlayBatch batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        boolean antiOcclusion = batch.antiOcclusion();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (antiOcclusion) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        } else {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
        }
        try {
            PoseStack poseStack = batch.poseSnapshot().createPoseStack();
            if (!antiOcclusion) {
                restorePreservedWorldDepth();
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(false);
            }
            for (DeferredOverlay overlay : batch.overlays()) {
                renderDeferredOverlay(overlay, poseStack, bufferSource, antiOcclusion);
            }
            bufferSource.endBatch();
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    private void renderDeferredOverlay(DeferredOverlay overlay, PoseStack poseStack,
                                       MultiBufferSource.BufferSource bufferSource, boolean antiOcclusion) {
        switch (overlay.kind()) {
            case BILLBOARD_CARD -> drawBillboardCard(overlay.lines(), overlay.localPos(), overlay.accentColor(),
                overlay.anchor() != null, poseStack, bufferSource, antiOcclusion);
            case TEXT_WINDOW -> drawTextWindowBillboard(overlay.text(), overlay.localPos(), overlay.palette(),
                overlay.fade(), overlay.anchor() != null, overlay.layout(), poseStack, bufferSource, antiOcclusion);
            case INPUT_BUBBLE -> drawInputBubbleBillboard(overlay.scenePoint(), overlay.direction(), overlay.icon(),
                overlay.text(), overlay.item(), overlay.fade(), overlay.layout(), poseStack, bufferSource,
                antiOcclusion);
        }
    }

    private void drawBillboardCard(List<Component> lines, Vec3 localPos, int accentColor, boolean withLeader,
                                   PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, boolean antiOcclusion) {
        if (lines.isEmpty()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        MultiBufferSource fontBuffer = antiOcclusion ? panelNoDepthBuffer : bufferSource;
        float textScale = 0.018F * OVERLAY_UI_SCALE;

        int totalHeight = lines.size() * font.lineHeight;
        int backgroundColor = 0x66000000;
        int textColor = 0xF2FFFFFF;
        float leader = withLeader ? OVERLAY_LEADER_LENGTH : 0.0F;

        poseStack.pushPose();
        poseStack.translate(localPos.x, localPos.y, localPos.z);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-textScale, -textScale, textScale);
        // billboard 局部空间：+x = 屏幕右侧。文本框/文本从 leader 处开始向右，垂直居中。
        poseStack.translate(0.0F, -(totalHeight + 2) / 2.0F, 0.0F);

        if (withLeader) {
            drawLocalLeaderLine(poseStack, leader, (totalHeight + 2) / 2.0F, accentColor, antiOcclusion);
        }

        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, LOCAL_OVERLAY_FOREGROUND_Z);
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            float y = i * font.lineHeight;
            int color = i == 0 ? accentColor | 0xFF000000 : textColor;
            if (antiOcclusion) {
                font.drawInBatch(
                    line,
                    leader,
                    y,
                    color,
                    false,
                    poseStack.last().pose(),
                    fontBuffer,
                    Font.DisplayMode.SEE_THROUGH,
                    backgroundColor,
                    LightTexture.FULL_BRIGHT);
            }
            font.drawInBatch(
                line,
                leader,
                y,
                color,
                false,
                poseStack.last().pose(),
                fontBuffer,
                Font.DisplayMode.NORMAL,
                antiOcclusion ? 0 : backgroundColor,
                LightTexture.FULL_BRIGHT);
        }
        poseStack.popPose();
        flushPanelBuffer(antiOcclusion);

        poseStack.popPose();
    }

    /** Draws the thin horizontal guide line inside the current billboard-local space, from the anchor (x=0,
     *  vertically centred on the card) rightward to the card's left edge. The line is rendered as a tiny quad in
     *  the same local coordinate system as the text box, so it inherits the same perspective scaling. */
    private void drawLocalLeaderLine(PoseStack poseStack, float leader, float centerY, int color,
                                     boolean antiOcclusion) {
        if (leader <= 0.0F) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, LOCAL_OVERLAY_LEADER_Z);

        float halfThickness = OVERLAY_LEADER_THICKNESS * 0.5F;
        drawImmediateGradientRect(
            poseStack.last().pose(),
            0.0F,
            centerY - halfThickness,
            leader,
            centerY + halfThickness,
            withAlpha(color, 0.95F),
            withAlpha(color, 0.75F),
            antiOcclusion);

        poseStack.popPose();
    }

    private void drawImmediateGradientRect(Matrix4f matrix, float left, float top, float right, float bottom,
                                           int leftColor, int rightColor, boolean antiOcclusion) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (antiOcclusion) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        } else {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        colorVertex(buffer, matrix, left, bottom, leftColor);
        colorVertex(buffer, matrix, right, bottom, rightColor);
        colorVertex(buffer, matrix, right, top, rightColor);
        colorVertex(buffer, matrix, left, top, leftColor);
        tesselator.end();
        if (antiOcclusion) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        } else {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
        }
    }

    private static void colorVertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, int color) {
        buffer.vertex(matrix, x, y, 0.0F)
            .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF)
            .endVertex();
    }

    private static void colorVertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, int color) {
        buffer.vertex(matrix, x, y, z)
            .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF)
            .endVertex();
    }

    private void renderBeamTriangle(Matrix4f matrix,
                                    float apexX, float apexY, float apexZ,
                                    float baseLeftX, float baseY, float baseLeftZ,
                                    float baseRightX, float baseRightY, float baseRightZ,
                                    int apexColor, int baseColor) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        colorVertex(buffer, matrix, apexX, apexY, apexZ, apexColor);
        colorVertex(buffer, matrix, baseRightX, baseRightY, baseRightZ, baseColor);
        colorVertex(buffer, matrix, baseRightX, baseRightY, baseRightZ, baseColor);
        colorVertex(buffer, matrix, baseLeftX, baseY, baseLeftZ, baseColor);

        tesselator.end();
    }

    private void renderBeamTrapezoid(Matrix4f matrix,
                                     float bottomLeftX, float bottomY, float bottomLeftZ,
                                     float bottomRightX, float bottomRightY, float bottomRightZ,
                                     float topRightX, float topY, float topRightZ,
                                     float topLeftX, float topLeftY, float topLeftZ,
                                     int bottomColor, int topColor) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        colorVertex(buffer, matrix, bottomLeftX, bottomY, bottomLeftZ, bottomColor);
        colorVertex(buffer, matrix, bottomRightX, bottomRightY, bottomRightZ, bottomColor);
        colorVertex(buffer, matrix, topRightX, topY, topRightZ, topColor);
        colorVertex(buffer, matrix, topLeftX, topLeftY, topLeftZ, topColor);

        tesselator.end();
    }

    private void renderBeamFace(Matrix4f matrix, Vec3 source, Vec3 baseLeft, Vec3 baseRight,
                                Vec3 outerRight, Vec3 outerLeft, ProjectionGlowColors colors) {
        renderBeamTriangle(
            matrix,
            (float) source.x, (float) source.y, (float) source.z,
            (float) baseLeft.x, (float) baseLeft.y, (float) baseLeft.z,
            (float) baseRight.x, (float) baseRight.y, (float) baseRight.z,
            colors.sourceColor(),
            colors.waistColor());
        renderBeamTrapezoid(
            matrix,
            (float) baseLeft.x, (float) baseLeft.y, (float) baseLeft.z,
            (float) baseRight.x, (float) baseRight.y, (float) baseRight.z,
            (float) outerRight.x, (float) outerRight.y, (float) outerRight.z,
            (float) outerLeft.x, (float) outerLeft.y, (float) outerLeft.z,
            colors.waistColor(),
            colors.topColor());
    }

    private static ProjectionGlowColors projectionGlowColors(RenderLayout layout, float breathe) {
        float sourceRed = clamp(layout.redTint() * lerp(1.0F, 0.76F, breathe), 0.0F, 1.0F);
        float sourceGreen = clamp(layout.greenTint() * lerp(1.0F, 0.92F, breathe), 0.0F, 1.0F);
        float waistRed = clamp(layout.redTint() * lerp(1.0F, 0.62F, breathe), 0.0F, 1.0F);
        float waistGreen = clamp(layout.greenTint() * lerp(1.0F, 0.84F, breathe), 0.0F, 1.0F);
        float topRed = clamp(layout.redTint() * lerp(1.0F, 0.52F, breathe), 0.0F, 1.0F);
        float topGreen = clamp(layout.greenTint() * lerp(1.0F, 0.78F, breathe), 0.0F, 1.0F);

        return new ProjectionGlowColors(
            argb(64, sourceRed, sourceGreen, 1.0F),
            argb(108, waistRed, waistGreen, 1.0F),
            argb(24, topRed, topGreen, 1.0F));
    }

    private static Vec3 beamCorner(Vec3 center, Vec3 side, Vec3 up, float sideOffset, float upOffset) {
        return center.add(side.scale(sideOffset)).add(up.scale(upOffset));
    }

    private static double lifeSizeBeamSourceX(Direction facing) {
        return switch (facing) {
            case EAST -> 1.0D - LIFE_SIZE_BEAM_FACE_INSET;
            case WEST -> LIFE_SIZE_BEAM_FACE_INSET;
            default -> LIFE_SIZE_BEAM_FACE_WIDTH;
        };
    }

    private static double lifeSizeBeamSourceZ(Direction facing) {
        return switch (facing) {
            case SOUTH -> 1.0D - LIFE_SIZE_BEAM_FACE_INSET;
            case NORTH -> LIFE_SIZE_BEAM_FACE_INSET;
            default -> LIFE_SIZE_BEAM_FACE_WIDTH;
        };
    }

    private static int withAlpha(int color, float alphaMultiplier) {
        int baseAlpha = (color >>> 24) & 0xFF;
        if (baseAlpha == 0) {
            baseAlpha = 0xFF;
        }
        int alpha = Math.max(0, Math.min(255, Math.round(baseAlpha * alphaMultiplier)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int argb(int alpha, float red, float green, float blue) {
        return (clampChannel(alpha) << 24)
            | (clampChannel(Math.round(red * 255.0F)) << 16)
            | (clampChannel(Math.round(green * 255.0F)) << 8)
            | clampChannel(Math.round(blue * 255.0F));
    }

    private static Vec3 miniatureBeamPoint(RenderLayout layout, float localX, float localY, float localZ) {
        Vec3 relative = new Vec3(
            localX,
            localY - layout.origin().y,
            localZ);
        Vec3 sceneRotated = layout.sceneRotation().apply(relative);
        Vec3 rotated = ProjectorSceneRotation.rotateY(sceneRotated, layout.rotationDegrees());
        return layout.origin().add(rotated);
    }

    private static float continueBeamHalfExtent(float sourceRadius, float waistHalfExtent,
                                                float sourceY, float waistY, float targetY) {
        float height = waistY - sourceY;
        if (Math.abs(height) < 1.0E-4F) {
            return waistHalfExtent;
        }
        float slope = (waistHalfExtent - sourceRadius) / height;
        return waistHalfExtent + slope * (targetY - waistY);
    }

    private static int clampChannel(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float lerp(float start, float end, float delta) {
        return start + (end - start) * delta;
    }

    private record ProjectionGlowColors(int sourceColor, int waistColor, int topColor) {
    }

    private void drawTextWindowBillboard(String text, Vec3 localPos, PonderPalette palette, float fade,
                                         boolean withLeader, RenderLayout layout, PoseStack poseStack,
                                         MultiBufferSource.BufferSource bufferSource, boolean antiOcclusion) {
        Font font = Minecraft.getInstance().font;
        MultiBufferSource fontBuffer = antiOcclusion ? panelNoDepthBuffer : bufferSource;
        List<FormattedText> lines = font.getSplitter().splitLines(text, 180, Style.EMPTY);
        if (lines.isEmpty()) {
            lines = List.of(FormattedText.of(text));
        }

        int boxWidth = 0;
        for (FormattedText line : lines) {
            boxWidth = Math.max(boxWidth, font.width(line));
        }
        boxWidth = Math.max(1, boxWidth);
        int boxHeight = Math.max(font.lineHeight, lines.size() * font.lineHeight);

        Color brighter = (palette == null ? PonderPalette.WHITE : palette)
            .getColorObject()
            .mixWith(new Color(0xff_ffffdd, true), 0.5f)
            .setImmutable();

        float leader = withLeader ? OVERLAY_LEADER_LENGTH : 0.0F;
        float uiScale = uiScaleFor(localPos, layout);
        poseStack.pushPose();
        poseStack.translate(localPos.x, localPos.y, localPos.z);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-uiScale, -uiScale, uiScale);
        // billboard 局部空间：+x = 屏幕右侧。文本框从 leader 处开始向右，垂直居中。
        poseStack.translate(0.0F, -(boxHeight + 6.0F) / 2.0F, 0.0F);

        if (withLeader) {
            int leaderColor = palette == null ? 0xE6FCFF : palette.getColor();
            drawLocalLeaderLine(poseStack, leader * fade, (boxHeight + 6.0F) / 2.0F, leaderColor, antiOcclusion);
        }

        GuiGraphics graphics = ProjectorGuiGraphicsBridge.create(poseStack, panelBuffer);
        new BoxElement()
            .withBackground(PonderUI.BACKGROUND_FLAT)
            .gradientBorder(TextWindowElement.COLOR_WINDOW_BORDER)
            .at(leader, 3, LOCAL_OVERLAY_BACKGROUND_Z)
            .withBounds(boxWidth, Math.max(1, boxHeight - 1))
            .render(graphics);
        flushPanelBuffer(antiOcclusion);

        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, LOCAL_OVERLAY_FOREGROUND_Z);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).getString();
            float y = 3 + font.lineHeight * i;
            int color = brighter.copy().scaleAlphaForText(fade).getRGB();
            if (antiOcclusion) {
                font.drawInBatch(
                    line,
                    leader,
                    y,
                    color,
                    false,
                    poseStack.last().pose(),
                    fontBuffer,
                    Font.DisplayMode.SEE_THROUGH,
                    0,
                    LightTexture.FULL_BRIGHT);
            }
            font.drawInBatch(
                line,
                leader,
                y,
                color,
                false,
                poseStack.last().pose(),
                fontBuffer,
                Font.DisplayMode.NORMAL,
                0,
                LightTexture.FULL_BRIGHT);
        }
        flushPanelBuffer(antiOcclusion);
        poseStack.popPose();
        poseStack.popPose();
    }

    private void drawInputBubbleBillboard(Vec3 scenePoint, Pointing direction, ScreenElement icon, String text,
                                          ItemStack item, float fade,
                                          RenderLayout layout, PoseStack poseStack,
                                          MultiBufferSource.BufferSource bufferSource, boolean antiOcclusion) {
        Font font = Minecraft.getInstance().font;

        boolean hasIcon = icon != null;
        boolean hasText = text != null && !text.isBlank();
        boolean hasItem = item != null && !item.isEmpty();
        int keyWidth = hasText ? font.width(text) : 0;
        int width = 0;
        int height = 0;

        if (hasIcon) {
            width += 24;
            height = 24;
        }
        if (hasText) {
            width += keyWidth;
        }
        if (hasItem) {
            width += 24;
            height = 24;
        }
        if (width <= 0 || height <= 0) {
            return;
        }

        float xFade = direction == Pointing.RIGHT ? -1.0F : direction == Pointing.LEFT ? 1.0F : 0.0F;
        float yFade = direction == Pointing.DOWN ? -1.0F : direction == Pointing.UP ? 1.0F : 0.0F;
        xFade *= 10.0F * (1.0F - fade);
        yFade *= 10.0F * (1.0F - fade);

        Vec3 localPos = layout.localPointFor(scenePoint);
        float scale = uiScaleFor(localPos, layout);

        poseStack.pushPose();
        poseStack.translate(localPos.x, localPos.y, localPos.z);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-scale, -scale, scale);
        poseStack.translate(xFade, yFade, 0.0F);

        GuiGraphics graphics = ProjectorGuiGraphicsBridge.create(poseStack, panelBuffer);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Layer 1 — background speech box + divot. renderSpeechBoxLocal leaves the pose translated to
        // the box's top-left corner, so the content below is positioned relative to it (native layout).
        renderSpeechBoxLocal(graphics, 0, 0, width, height, false, direction,
            LOCAL_OVERLAY_BACKGROUND_Z,
            LOCAL_OVERLAY_FOREGROUND_Z);
        flushPanelBuffer(antiOcclusion);

        // Layer 2 — the item as a real 3D model, drawn after (on top of) the box.
        if (hasItem) {
            drawPanelItem(poseStack, item, keyWidth + (hasIcon ? 24 : 0), antiOcclusion);
        }

        // Layer 3 — key text + icon. They sit beside the item (never overlapping it), so a tiny lift
        // above the box is enough to keep them on top without floating away from the panel.
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, LOCAL_OVERLAY_FOREGROUND_Z);
        if (hasText) {
            int color = PonderPalette.WHITE.getColorObject().copy().scaleAlpha(fade).getRGB();
            font.drawInBatch(text, 2.0F, (height - font.lineHeight) / 2.0F + 2.0F, color, false,
                poseStack.last().pose(), antiOcclusion ? panelNoDepthBuffer : bufferSource,
                Font.DisplayMode.NORMAL, 0,
                LightTexture.FULL_BRIGHT);
        }
        if (hasIcon) {
            if (antiOcclusion) {
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
            } else {
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);
            }
            poseStack.pushPose();
            poseStack.translate(keyWidth, 0.0F, 0.0F);
            // Squash the icon completely flat onto the box plane (z-scale = 0). The icon may be a JEI ingredient
            // (fluid, Mekanism chemical, ...) whose renderer bakes a GUI z-level straight into its geometry through
            // the pose matrix — e.g. Mekanism's chemical renderer emits its sprite at z=100. The billboard scale
            // then magnifies that z (~1.5 * 0.018 per unit), so the sprite floats well in front of the panel.
            // A 0 z-scale here collapses every such internal z-level onto the plane. Repair the (now singular)
            // normal matrix afterward so a lit 3D model routed through here would not render dark; flat 2D
            // ingredient sprites ignore normals, so this is harmless for them.
            Matrix3f iconNormal = new Matrix3f(poseStack.last().normal());
            poseStack.scale(1.0F, 1.0F, 0.0F);
            poseStack.last().normal().set(iconNormal);
            poseStack.scale(1.5F, 1.5F, 1.5F);
            icon.render(graphics, 0, 0);
            poseStack.popPose();
        }
        flushPanelBuffer(antiOcclusion);
        poseStack.popPose();

        poseStack.popPose();
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Renders the show_controls item as a real 3D model on top of the speech box, using the same recipe
     * Create's {@link net.createmod.catnip.gui.element.GuiGameElement} uses for in-GUI items (cull on,
     * {@link UIRenderHelper#flipForGuiRender}). Unlike GuiGameElement it draws into our private
     * {@link #panelBuffer} — never the shared world buffer, so nothing else is contaminated — and
     * saves/restores the level diffuse light directions that {@link Lighting} would otherwise clobber,
     * keeping world entity lighting intact. {@code x} is the item's left edge relative to the box
     * top-left corner.
     * <p>
     * For block entities (chests, shulker boxes, etc.) with multiple render layers, depth testing is
     * enabled during the item render to preserve correct layer ordering, then cleared afterward to keep
     * the overall item on top of the background box.
     */
    private void drawPanelItem(PoseStack poseStack, ItemStack item, int x, boolean antiOcclusion) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemRenderer itemRenderer = minecraft.getItemRenderer();
        BakedModel model = itemRenderer.getModel(item, null, null, 0);
        boolean flatLighting = !model.usesBlockLight();

        Vector3f savedLight0 = null;
        Vector3f savedLight1 = null;
        try {
            Vector3f[] dirs = RenderSystemShaderLightsAccessor.ponderer$getShaderLightDirections();
            if (dirs != null && dirs.length >= 2 && dirs[0] != null && dirs[1] != null) {
                savedLight0 = new Vector3f(dirs[0]);
                savedLight1 = new Vector3f(dirs[1]);
            }
        } catch (Throwable ignored) {
        }

        if (flatLighting) {
            Lighting.setupForFlatItems();
        } else {
            Lighting.setupFor3DItems();
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        minecraft.getTextureManager().getTexture(InventoryMenu.BLOCK_ATLAS).setFilter(false, false);

        poseStack.pushPose();
        // Lift the item onto the same foreground layer as the TextWindow text so a flat,
        // depthless item — a stick, an apple — does not z-fight the box background sitting behind it.
        poseStack.translate(x, 0.0F, LOCAL_OVERLAY_FOREGROUND_Z);
        // Squash the item's depth toward the camera so it sits on the box's plane (no parallax) yet keeps
        // its inventory-style 3D silhouette. Applied first so it compresses the whole model along view-z.
        // A 0 z-scale makes the normal matrix singular and the item renders dark, so repair normals after
        // squashing: block-lit models keep the billboard normal basis, while generated flat items need the
        // identity GUI normal basis that Lighting.setupForFlatItems() is built around.
        Matrix3f litNormal = new Matrix3f(poseStack.last().normal());
        poseStack.scale(1.0F, 1.0F, PANEL_ITEM_FLATTEN);
        if (flatLighting) {
            poseStack.last().normal().identity();
        } else {
            poseStack.last().normal().set(litNormal);
        }
        poseStack.scale(1.5F, 1.5F, 1.5F);
        UIRenderHelper.flipForGuiRender(poseStack);
        poseStack.translate(8.0F, -8.0F, 0.0F);
        poseStack.scale(16.0F, 16.0F, 16.0F);

        // Enable depth testing for the item render so multi-layer block entities (chests, shulker boxes)
        // render with correct internal layer ordering. But the projected ponder scene has already written
        // its depth, so a plain depth test would let scene blocks closer than the panel occlude the item.
        // Compress the item's depth range into the very front of the buffer ([0, FRONT]) so it always passes
        // the depth test against the scene (which spans the full [0, 1]) yet still sorts its own layers among
        // each other within that thin window — the same trick vanilla uses to float GUI items above the world.
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(515); // GL_LEQUAL - normal depth test
        if (antiOcclusion) {
            GL11.glDepthRange(0.0D, PANEL_ITEM_DEPTH_FRONT);
        }
        itemRenderer.render(item, ItemDisplayContext.GUI, false, poseStack, panelBuffer,
            LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
        panelBuffer.endBatch();
        GL11.glDepthRange(0.0D, 1.0D); // restore default depth range
        if (antiOcclusion) {
            // Restore no-depth state for subsequent layers (text, icon)
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        } else {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
        }

        poseStack.popPose();

        if (savedLight0 != null && savedLight1 != null) {
            RenderSystem.setShaderLights(savedLight0, savedLight1);
        }
    }

    private RenderType panelNoDepthRenderType(RenderType type) {
        return panelNoDepthRenderTypes.computeIfAbsent(type, ProjectorBlockEntityRenderer::wrapNoDepthRenderType);
    }

    private static RenderType wrapNoDepthRenderType(RenderType type) {
        return new RenderType(
            "ponderer_panel_no_depth_" + type,
            type.format(),
            type.mode(),
            type.bufferSize(),
            type.affectsCrumbling(),
            !type.canConsolidateConsecutiveGeometry(),
            () -> {
                type.setupRenderState();
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
            },
            () -> {
                type.clearRenderState();
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
            }) {
        };
    }

    private void flushPanelBuffer(boolean antiOcclusion) {
        if (antiOcclusion) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        } else {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
        }
        panelBuffer.endBatch();
        if (antiOcclusion) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        } else {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
        }
    }

    private static void renderSpeechBoxLocal(GuiGraphics graphics, int x, int y, int w, int h, boolean highlighted,
                                             Pointing pointing, float backgroundZ, float foregroundZ) {
        PoseStack poseStack = graphics.pose();

        int boxX = x;
        int boxY = y;
        int divotX = x;
        int divotY = y;
        int divotRotation = 0;
        int divotSize = 8;
        int distance = 1;
        int divotRadius = divotSize / 2;
        var borderColors = highlighted ? net.createmod.ponder.foundation.ui.PonderButton.COLOR_HOVER : PonderUI.COLOR_IDLE;
        Color arrowColor;

        switch (pointing) {
            case DOWN -> {
                boxX -= w / 2;
                boxY -= h + divotSize + 1 + distance;
                divotX -= divotRadius;
                divotY -= divotSize + distance;
                arrowColor = borderColors.getSecond();
            }
            case LEFT -> {
                divotRotation = 90;
                boxX += divotSize + 1 + distance;
                boxY -= h / 2;
                divotX += distance;
                divotY -= divotRadius;
                arrowColor = Color.mixColors(borderColors, 0.5F);
            }
            case RIGHT -> {
                divotRotation = 270;
                boxX -= w + divotSize + 1 + distance;
                boxY -= h / 2;
                divotX -= divotSize + distance;
                divotY -= divotRadius;
                arrowColor = Color.mixColors(borderColors, 0.5F);
            }
            case UP -> {
                divotRotation = 180;
                boxX -= w / 2;
                boxY += divotSize + 1 + distance;
                divotX -= divotRadius;
                divotY += distance;
                arrowColor = borderColors.getFirst();
            }
            default -> {
                boxX -= w / 2;
                boxY -= h + divotSize + 1 + distance;
                divotX -= divotRadius;
                divotY -= divotSize + distance;
                arrowColor = borderColors.getSecond();
            }
        }

        new BoxElement()
            .withBackground(PonderUI.BACKGROUND_FLAT)
            .gradientBorder(borderColors)
            .at(boxX, boxY, backgroundZ)
            .withBounds(w, h)
            .render(graphics);

        poseStack.pushPose();
        poseStack.translate(divotX + divotRadius, divotY + divotRadius, foregroundZ);
        poseStack.mulPose(Axis.ZP.rotationDegrees(divotRotation));
        poseStack.translate(-divotRadius, -divotRadius, 0);
        net.createmod.ponder.enums.PonderGuiTextures.SPEECH_TOOLTIP_BACKGROUND.render(graphics, 0, 0);
        net.createmod.ponder.enums.PonderGuiTextures.SPEECH_TOOLTIP_COLOR.render(graphics, 0, 0, arrowColor);
        poseStack.popPose();

        poseStack.translate(boxX, boxY, 0);
    }

    private String resolveText(TextWindowElementAccessor accessor) {
        String text = accessor.ponderer$getBakedText();
        if (text != null && !text.isBlank()) {
            return text;
        }

        Supplier<String> getter = accessor.ponderer$getTextGetter();
        if (getter == null) {
            return "";
        }

        String resolved = getter.get();
        return resolved == null ? "" : resolved;
    }

    private int fallbackLaneForY(int y, int fallbackLane) {
        if (y <= 0) {
            return fallbackLane;
        }
        return Math.max(fallbackLane, Math.min(6, y / 32));
    }

    private float uiScaleFor(Vec3 localPos, RenderLayout layout) {
        return 0.018F * layout.uiScale() * OVERLAY_UI_SCALE;
    }

    @Override
    public boolean shouldRenderOffScreen(ProjectorBlockEntity blockEntity) {
        // The projected scene can be visible even when the projector block's chunk is outside the frustum.
        return blockEntity.hasRenderableScene();
    }

    @Override
    public int getViewDistance() {
        return (int) ProjectorRenderDistances.PROJECTION_RENDER_DISTANCE;
    }

    @Override
    public boolean shouldRender(ProjectorBlockEntity blockEntity, Vec3 cameraPos) {
        return ProjectorRenderBounds.distanceToRenderBoundsSqr(blockEntity, cameraPos)
            <= ProjectorRenderDistances.PROJECTION_RENDER_DISTANCE_SQR;
    }

    private static double distanceToRenderBoundsSqr(ProjectorBlockEntity blockEntity) {
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return ProjectorRenderBounds.distanceToRenderBoundsSqr(blockEntity, cameraPos);
    }

    record DeferredProjectionGlow(PoseSnapshot poseSnapshot, RenderLayout layout, Direction facing,
                                  float partialTick) {
    }

    private record RenderLayout(ProjectorKind kind, Vec3 origin, Vec3 rotationPivot, Vec3 sceneTranslate, float scale,
                                float rotationDegrees, ProjectorSceneRotation sceneRotation, BoundingBox bounds,
                                float redTint, float greenTint, float blueTint, float uiScale) {
        static RenderLayout from(ProjectorBlockEntity blockEntity, BoundingBox bounds,
                                 PonderScene activeScene, float partialTick) {
            ProjectorKind kind = blockEntity.getProjectorKind();
            float rotation = blockEntity.getSceneRotationDegrees();
            ProjectorSceneRotation sceneRotation = kind == ProjectorKind.MINIATURE
                ? ProjectorSceneRotation.from(activeScene, partialTick)
                : ProjectorSceneRotation.NONE;

            if (kind == ProjectorKind.MINIATURE) {
                int spanX = Math.max(1, bounds.getXSpan());
                int spanZ = Math.max(1, bounds.getZSpan());
                float miniatureScale = blockEntity.getMiniatureScale();
                float scale = MINIATURE_FILL / Math.max(spanX, spanZ) * miniatureScale;
                double centerX = (bounds.minX() + bounds.maxX() + 1) * 0.5D;
                double centerY = bounds.minY();
                double centerZ = (bounds.minZ() + bounds.maxZ() + 1) * 0.5D;
                float globalTextScale = Config.PROJECTOR_MINIATURE_TEXT_SCALE.get().floatValue();
                float perProjectorScale = blockEntity.getTextScale();
                return new RenderLayout(
                    kind,
                    new Vec3(0.5D, MINIATURE_Y_OFFSET, 0.5D),
                    Vec3.ZERO,
                    new Vec3(-centerX, -centerY, -centerZ),
                    scale,
                    rotation,
                    sceneRotation,
                    bounds,
                    tint(blockEntity, 0.76F), tint(blockEntity, 0.96F), tint(blockEntity, 1.00F),
                    scale * globalTextScale * perProjectorScale);
            }

            BlockPos anchor = blockEntity.getProjectionAnchor();
            Vec3 offset = new Vec3(
                anchor.getX() - blockEntity.getBlockPos().getX(),
                anchor.getY() - blockEntity.getBlockPos().getY(),
                anchor.getZ() - blockEntity.getBlockPos().getZ());
            float globalTextScale = Config.PROJECTOR_LIFE_SIZE_TEXT_SCALE.get().floatValue();
            float perProjectorScale = blockEntity.getTextScale();
            return new RenderLayout(
                kind,
                offset,
                new Vec3(0.5D, 0.0D, 0.5D),
                new Vec3(-0.5D, 0.0D, -0.5D),
                1.0F,
                rotation,
                sceneRotation,
                bounds,
                tint(blockEntity, 0.72F), tint(blockEntity, 0.88F), tint(blockEntity, 1.00F),
                globalTextScale * perProjectorScale);
        }

        Vec3 localPointFor(Vec3 scenePoint) {
            Vec3 translated = scenePoint.add(sceneTranslate);
            double scaledX = translated.x * scale;
            double scaledY = translated.y * scale;
            double scaledZ = translated.z * scale;
            Vec3 sceneRotated = sceneRotation.apply(new Vec3(scaledX, scaledY, scaledZ));
            Vec3 rotated = ProjectorSceneRotation.rotateY(sceneRotated, rotationDegrees);
            return origin.add(rotationPivot).add(rotated);
        }

        Vec3 fallbackCardPosition(int lane) {
            int safeLane = Math.max(0, lane);
            if (kind == ProjectorKind.MINIATURE) {
                return new Vec3(0.5D, MINIATURE_Y_OFFSET + 0.46D + safeLane * 0.18D, 0.5D);
            }

            double centerX = (bounds.minX() + bounds.maxX() + 1) * 0.5D;
            double centerY = bounds.maxY() + 1.0D;
            double centerZ = (bounds.minZ() + bounds.maxZ() + 1) * 0.5D;
            return localPointFor(new Vec3(centerX, centerY, centerZ))
                .add(0.0D, LIFE_SIZE_CARD_RISE + safeLane * 0.28D, 0.0D);
        }

        private static float tint(ProjectorBlockEntity blockEntity, float tintedValue) {
            return blockEntity.showBlueTint() ? tintedValue : 1.0F;
        }
    }

    record DeferredOverlayBatch(PoseSnapshot poseSnapshot, List<DeferredOverlay> overlays, boolean antiOcclusion) {
        static DeferredOverlayBatch empty() {
            return new DeferredOverlayBatch(PoseSnapshot.identity(), List.of(), true);
        }

        static DeferredOverlayBatch of(PoseSnapshot poseSnapshot, List<DeferredOverlay> overlays,
                                       boolean antiOcclusion) {
            if (overlays == null || overlays.isEmpty()) {
                return empty();
            }
            return new DeferredOverlayBatch(poseSnapshot, List.copyOf(overlays), antiOcclusion);
        }

        static DeferredOverlayBatch combine(DeferredOverlayBatch first, DeferredOverlayBatch second) {
            boolean firstEmpty = first == null || first.isEmpty();
            boolean secondEmpty = second == null || second.isEmpty();
            if (firstEmpty && secondEmpty) {
                return empty();
            }
            if (firstEmpty) {
                return second;
            }
            if (secondEmpty) {
                return first;
            }

            List<DeferredOverlay> combined = new ArrayList<>(first.overlays().size() + second.overlays().size());
            combined.addAll(first.overlays());
            combined.addAll(second.overlays());
            return new DeferredOverlayBatch(first.poseSnapshot(), List.copyOf(combined), first.antiOcclusion());
        }

        boolean isEmpty() {
            return overlays.isEmpty();
        }
    }

    private record PoseSnapshot(Matrix4f pose, Matrix3f normal) {
        static PoseSnapshot capture(PoseStack poseStack) {
            return new PoseSnapshot(new Matrix4f(poseStack.last().pose()), new Matrix3f(poseStack.last().normal()));
        }

        static PoseSnapshot identity() {
            return new PoseSnapshot(new Matrix4f(), new Matrix3f());
        }

        PoseStack createPoseStack() {
            PoseStack poseStack = new PoseStack();
            poseStack.last().pose().set(pose);
            poseStack.last().normal().set(normal);
            return poseStack;
        }
    }

    private record DeferredOverlay(Kind kind, Vec3 localPos, Vec3 anchor, List<Component> lines, int accentColor,
                                   String text, PonderPalette palette, float fade, Vec3 scenePoint,
                                   Pointing direction, ScreenElement icon, ItemStack item, RenderLayout layout) {
        static DeferredOverlay billboardCard(Vec3 localPos, List<Component> lines, int accentColor) {
            return new DeferredOverlay(Kind.BILLBOARD_CARD, localPos, null, List.copyOf(lines), accentColor, "",
                null, 1.0F, null, null, null, ItemStack.EMPTY, null);
        }

        /** A billboard card anchored at a scene point: the box+text sit to the screen-right of {@code localPos}
         *  with a horizontal guide line bridging the gap (anchor is non-null to flag the leader). */
        static DeferredOverlay billboardCardAnchored(Vec3 anchor, List<Component> lines, int accentColor) {
            return new DeferredOverlay(Kind.BILLBOARD_CARD, anchor, anchor, List.copyOf(lines), accentColor, "",
                null, 1.0F, null, null, null, ItemStack.EMPTY, null);
        }

        static DeferredOverlay textWindow(String text, Vec3 localPos, PonderPalette palette, float fade,
                                          Vec3 anchor, RenderLayout layout) {
            return new DeferredOverlay(Kind.TEXT_WINDOW, localPos, anchor, List.of(), 0, text,
                palette, fade, null, null, null, ItemStack.EMPTY, layout);
        }

        static DeferredOverlay inputBubble(Vec3 scenePoint, Pointing direction, ScreenElement icon,
                                           String text, ItemStack item, float fade, RenderLayout layout) {
            return new DeferredOverlay(Kind.INPUT_BUBBLE, null, null, List.of(), 0, text,
                null, fade, scenePoint, direction, icon, item.copy(), layout);
        }

        enum Kind {
            BILLBOARD_CARD,
            TEXT_WINDOW,
            INPUT_BUBBLE
        }
    }


    private record ProjectedRenderTypeBuffer(DefaultSuperRenderTypeBuffer delegate, float red, float green,
                                             float blue) implements SuperRenderTypeBuffer {

        @Override
        public VertexConsumer getEarlyBuffer(RenderType type) {
            return tinted(delegate.getEarlyBuffer(type));
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return tinted(delegate.getBuffer(type));
        }

        @Override
        public VertexConsumer getLateBuffer(RenderType type) {
            return tinted(delegate.getLateBuffer(type));
        }

        @Override
        public void draw() {
            delegate.draw();
        }

        @Override
        public void draw(RenderType type) {
            delegate.draw(type);
        }

        private VertexConsumer tinted(VertexConsumer consumer) {
            if (red == 1.0F && green == 1.0F && blue == 1.0F) {
                return consumer;
            }
            return new ColoringVertexConsumer(consumer, red, green, blue, 1.0F);
        }
    }
}
