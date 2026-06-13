package com.nododiiiii.ponderer.projector.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;import com.mojang.math.Axis;
import com.nododiiiii.ponderer.mixin.InputWindowElementAccessor;
import com.nododiiiii.ponderer.mixin.RenderSystemShaderLightsAccessor;
import com.nododiiiii.ponderer.mixin.TextWindowElementAccessor;
import com.nododiiiii.ponderer.projector.ProjectorBlock;
import com.nododiiiii.ponderer.projector.ProjectorBlockEntity;
import com.nododiiiii.ponderer.projector.ProjectorKind;
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

import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class ProjectorBlockEntityRenderer implements BlockEntityRenderer<ProjectorBlockEntity> {

    private static final float MINIATURE_FILL = 0.85F;
    private static final float MINIATURE_Y_OFFSET = 1.06F;
    private static final float MINIATURE_CARD_RISE = 0.22F;
    private static final float LIFE_SIZE_CARD_RISE = 0.85F;
    /** Tiny z lift (toward camera) shared by flat overlay foreground content so it never z-fights the box it
     *  sits on: the TextWindow body text, the speech-box divot, and the show_controls panel item all use it.
     *  叠加层前景内容的微小 z 提升（朝向相机），避免与背景框发生 z-fighting：TextWindow 正文、气泡框尖角和
     *  show_controls 面板物品都使用此值。*/
    private static final float LOCAL_OVERLAY_TEXT_Z = 0.02F;
    /** Larger lift for the input panel's key text + icon, which sit beside (not on top of) the item.
     *  输入面板按键文本和图标的较大提升值，它们位于物品旁边（而非其上方）。*/
    private static final float PANEL_FG_Z = 2.0F;
    /** Negative z offset (away from camera) for leader lines so they render behind text boxes but still
     *  use no-depth rendering to avoid block occlusion.
     *  引导线的负 z 偏移（远离相机），使其渲染在文本框下方，但仍使用无深度测试以避免被方块遮挡。*/
    private static final float LEADER_LINE_Z_OFFSET = -0.01F;
    /** Global scale multiplier for all projected overlay UI (text windows, panels, cards).
     *  Base scale is 1.0; default 2.0 doubles the size of text, boxes, and line width.
     *  所有投影叠加层 UI（文本窗口、面板、卡片）的全局缩放倍数。
     *  基础缩放为 1.0；默认 2.0 将文本、框体和线宽的大小翻倍。*/
    private static final float OVERLAY_UI_SCALE = 1.8F;
    /** Horizontal leader length (in billboard-local font pixels) between the anchor point and the left edge of an
     *  anchored text card. The card text and box are drawn to the screen-right of the anchor by this much, with a
     *  thin horizontal guide line bridging the gap — mirroring Ponder's {@code TextWindowElement} layout. */
    private static final float OVERLAY_LEADER_LENGTH = 18.0F;
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

    /**
     * Private, isolated buffer for the show_controls panel. Never the shared world buffer source, so
     * flushing it mid-frame cannot disturb any other world rendering.
     */
    private final MultiBufferSource.BufferSource panelBuffer =
        MultiBufferSource.immediate(new BufferBuilder(256));
    private final Map<RenderType, RenderType> panelNoDepthRenderTypes = new IdentityHashMap<>();
    private final MultiBufferSource panelNoDepthBuffer =
        type -> panelBuffer.getBuffer(panelNoDepthRenderType(type));

    /**
     * No-depth lines RenderType for leader lines. Cached lazily on first use.
     */
    private RenderType linesNoDepth;

    public ProjectorBlockEntityRenderer() {
    }

    public ProjectorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ProjectorBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        ProjectorPlaybackState.PreparedFrame prepared = ProjectorPlaybackState.forBlock(blockEntity).prepare(blockEntity, partialTick);
        if (prepared == null) {
            return;
        }

        RenderLayout layout = RenderLayout.from(blockEntity, prepared.bundle().combinedBounds());
        PoseSnapshot overlayBasePose = PoseSnapshot.capture(poseStack);
        renderProjectedScene(prepared.activeScene(), layout, poseStack, prepared.localTick(), partialTick);
        DeferredOverlayBatch deferredNativeOverlay = captureNativePonderOverlays(prepared.activeScene(), layout, partialTick,
            overlayBasePose);
        List<ProjectorSceneBundle.OverlayCue> cues = prepared.bundle().activeCues(prepared.globalTick());
        DeferredOverlayBatch deferredCueOverlay = DeferredOverlayBatch.empty();
        if (!prepared.segment().extractRuntimeOverlays() || deferredNativeOverlay.isEmpty()) {
            deferredCueOverlay = captureOverlayCues(cues, layout, overlayBasePose);
        }
        DeferredOverlayBatch combinedOverlays = DeferredOverlayBatch.combine(deferredNativeOverlay, deferredCueOverlay);
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
        poseStack.mulPose(Axis.YP.rotationDegrees(layout.rotationDegrees()));
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

    private DeferredOverlayBatch captureNativePonderOverlays(PonderScene scene, RenderLayout layout, float partialTick,
                                                             PoseSnapshot overlayBasePose) {
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

        return DeferredOverlayBatch.of(overlayBasePose, overlays);
    }

    private DeferredOverlayBatch captureOverlayCues(List<ProjectorSceneBundle.OverlayCue> cues, RenderLayout layout,
                                                    PoseSnapshot overlayBasePose) {
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
        return DeferredOverlayBatch.of(overlayBasePose, overlays);
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
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        try {
            PoseStack poseStack = batch.poseSnapshot().createPoseStack();
            for (DeferredOverlay overlay : batch.overlays()) {
                renderDeferredOverlay(overlay, poseStack, bufferSource);
            }
            bufferSource.endBatch();
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    private void renderDeferredOverlay(DeferredOverlay overlay, PoseStack poseStack,
                                       MultiBufferSource bufferSource) {
        switch (overlay.kind()) {
            case POINTER -> drawPointerLine(overlay.anchor(), overlay.localPos(), poseStack, bufferSource,
                overlay.accentColor());
            case BILLBOARD_CARD -> drawBillboardCard(overlay.lines(), overlay.localPos(), overlay.accentColor(),
                overlay.anchor() != null, poseStack, bufferSource);
            case TEXT_WINDOW -> drawTextWindowBillboard(overlay.text(), overlay.localPos(), overlay.palette(),
                overlay.fade(), overlay.anchor() != null, overlay.layout(), poseStack, bufferSource);
            case INPUT_BUBBLE -> drawInputBubbleBillboard(overlay.scenePoint(), overlay.direction(), overlay.icon(),
                overlay.text(), overlay.item(), overlay.fade(), overlay.layout(), poseStack);
        }
    }

    private void drawPointerLine(Vec3 anchor, Vec3 textBoxLeft, PoseStack poseStack,
                                 MultiBufferSource bufferSource, int color) {
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, LEADER_LINE_Z_OFFSET);

        RenderSystem.lineWidth(OVERLAY_UI_SCALE);

        VertexConsumer consumer = bufferSource.getBuffer(getLinesNoDepth());
        Matrix4f matrix = poseStack.last().pose();
        // 水平引导线：从附着点水平延伸到文本框左边缘
        consumer.vertex(matrix, (float) anchor.x, (float) anchor.y, (float) anchor.z)
            .color(red, green, blue, 0.95F)
            .normal(0.0F, 1.0F, 0.0F)
            .endVertex();
        consumer.vertex(matrix, (float) textBoxLeft.x, (float) textBoxLeft.y, (float) textBoxLeft.z)
            .color(red, green, blue, 0.75F)
            .normal(0.0F, 1.0F, 0.0F)
            .endVertex();

        RenderSystem.lineWidth(1.0F);

        poseStack.popPose();
    }

    private void drawBillboardCard(List<Component> lines, Vec3 localPos, int accentColor, boolean withLeader,
                                   PoseStack poseStack, MultiBufferSource bufferSource) {
        if (lines.isEmpty()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
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
            drawLocalLeaderLine(poseStack, bufferSource, leader, (totalHeight + 2) / 2.0F, accentColor, textScale);
        }

        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            float y = i * font.lineHeight;
            int color = i == 0 ? accentColor | 0xFF000000 : textColor;
            font.drawInBatch(
                line,
                leader,
                y,
                color,
                false,
                poseStack.last().pose(),
                bufferSource,
                Font.DisplayMode.SEE_THROUGH,
                backgroundColor,
                LightTexture.FULL_BRIGHT);
            font.drawInBatch(
                line,
                leader,
                y,
                color,
                false,
                poseStack.last().pose(),
                bufferSource,
                Font.DisplayMode.NORMAL,
                0,
                LightTexture.FULL_BRIGHT);
        }

        poseStack.popPose();
    }

    /** Draws the thin horizontal guide line inside the current billboard-local space, from the anchor (x=0,
     *  vertically centred on the card) rightward to the card's left edge. Uses no-depth rendering with a
     *  negative z offset so the line renders behind text boxes. Line width scales with UI scale. */
    private void drawLocalLeaderLine(PoseStack poseStack, MultiBufferSource bufferSource, float leader,
                                     float centerY, int color, float uiScale) {
        if (leader <= 0.0F) {
            return;
        }
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, LEADER_LINE_Z_OFFSET);

        // Scale line width with UI scale (which increases as player gets closer)
        float lineWidth = OVERLAY_UI_SCALE * (uiScale / (0.018F * OVERLAY_UI_SCALE));
        RenderSystem.lineWidth(lineWidth);

        VertexConsumer consumer = bufferSource.getBuffer(getLinesNoDepth());
        Matrix4f matrix = poseStack.last().pose();
        consumer.vertex(matrix, 0.0F, centerY, 0.0F)
            .color(red, green, blue, 0.95F)
            .normal(1.0F, 0.0F, 0.0F)
            .endVertex();
        consumer.vertex(matrix, leader, centerY, 0.0F)
            .color(red, green, blue, 0.75F)
            .normal(1.0F, 0.0F, 0.0F)
            .endVertex();

        RenderSystem.lineWidth(1.0F);

        poseStack.popPose();
    }

    private void drawTextWindowBillboard(String text, Vec3 localPos, PonderPalette palette, float fade,
                                         boolean withLeader, RenderLayout layout, PoseStack poseStack,
                                         MultiBufferSource bufferSource) {
        Font font = Minecraft.getInstance().font;
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
            drawLocalLeaderLine(poseStack, bufferSource, leader, (boxHeight + 6.0F) / 2.0F, leaderColor, uiScale);
        }

        GuiGraphics graphics = ProjectorGuiGraphicsBridge.create(poseStack);
        new BoxElement()
            .withBackground(PonderUI.BACKGROUND_FLAT)
            .gradientBorder(TextWindowElement.COLOR_WINDOW_BORDER)
            .at(leader, 3, 0)
            .withBounds(boxWidth, Math.max(1, boxHeight - 1))
            .render(graphics);

        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, LOCAL_OVERLAY_TEXT_Z);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).getString();
            float y = 3 + font.lineHeight * i;
            int color = brighter.copy().scaleAlphaForText(fade).getRGB();
            font.drawInBatch(
                line,
                leader,
                y,
                color,
                false,
                poseStack.last().pose(),
                bufferSource,
                Font.DisplayMode.SEE_THROUGH,
                0,
                LightTexture.FULL_BRIGHT);
            font.drawInBatch(
                line,
                leader,
                y,
                color,
                false,
                poseStack.last().pose(),
                bufferSource,
                Font.DisplayMode.NORMAL,
                0,
                LightTexture.FULL_BRIGHT);
        }
        poseStack.popPose();
        poseStack.popPose();
    }

    private void drawInputBubbleBillboard(Vec3 scenePoint, Pointing direction, ScreenElement icon, String text,
                                          ItemStack item, float fade,
                                          RenderLayout layout, PoseStack poseStack) {
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
        renderSpeechBoxLocal(graphics, 0, 0, width, height, false, direction);
        flushPanelBufferNoDepth();

        // Layer 2 — the item as a real 3D model, drawn after (on top of) the box.
        if (hasItem) {
            drawPanelItem(poseStack, item, keyWidth + (hasIcon ? 24 : 0));
        }

        // Layer 3 — key text + icon. They sit beside the item (never overlapping it), so a tiny lift
        // above the box is enough to keep them on top without floating away from the panel.
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, LOCAL_OVERLAY_TEXT_Z);
        if (hasText) {
            int color = PonderPalette.WHITE.getColorObject().copy().scaleAlpha(fade).getRGB();
            font.drawInBatch(text, 2.0F, (height - font.lineHeight) / 2.0F + 2.0F, color, false,
                poseStack.last().pose(), panelNoDepthBuffer, Font.DisplayMode.NORMAL, 0,
                LightTexture.FULL_BRIGHT);
        }
        if (hasIcon) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
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
        flushPanelBufferNoDepth();
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
    private void drawPanelItem(PoseStack poseStack, ItemStack item, int x) {
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
        // Lift the item just off the box plane (the same tiny offset the TextWindow text uses) so a flat,
        // depthless item — a stick, an apple — does not z-fight the box background sitting behind it.
        poseStack.translate(x, 0.0F, LOCAL_OVERLAY_TEXT_Z);
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
        GL11.glDepthRange(0.0D, PANEL_ITEM_DEPTH_FRONT);
        itemRenderer.render(item, ItemDisplayContext.GUI, false, poseStack, panelBuffer,
            LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
        panelBuffer.endBatch();
        GL11.glDepthRange(0.0D, 1.0D); // restore default depth range
        // Restore no-depth state for subsequent layers (text, icon)
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

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

    /**
     * Returns a no-depth lines RenderType for leader lines, lazily creating it on first use.
     * Lines drawn with this type ignore depth testing (never occluded by blocks) but can still
     * be occluded by subsequent no-depth content like panels.
     */
    private RenderType getLinesNoDepth() {
        if (linesNoDepth == null) {
            RenderType base = RenderType.lines();
            linesNoDepth = wrapNoDepthRenderType(base);
        }
        return linesNoDepth;
    }

    private void flushPanelBufferNoDepth() {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        panelBuffer.endBatch();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
    }

    private static void renderSpeechBoxLocal(GuiGraphics graphics, int x, int y, int w, int h, boolean highlighted,
                                             Pointing pointing) {
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
            .at(boxX, boxY, 0)
            .withBounds(w, h)
            .render(graphics);

        poseStack.pushPose();
        poseStack.translate(divotX + divotRadius, divotY + divotRadius, LOCAL_OVERLAY_TEXT_Z);
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

    private float uiScaleFor(Vec3 localPos) {
        return 0.018F * OVERLAY_UI_SCALE;
    }

    @Override
    public boolean shouldRenderOffScreen(ProjectorBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean shouldRender(ProjectorBlockEntity blockEntity, Vec3 cameraPos) {
        return true;
    }

    private record RenderLayout(ProjectorKind kind, Vec3 origin, Vec3 sceneTranslate, float scale,
                                float rotationDegrees, BoundingBox bounds,
                                float redTint, float greenTint, float blueTint, float uiScale) {
        static RenderLayout from(ProjectorBlockEntity blockEntity, BoundingBox bounds) {
            Direction facing = blockEntity.getBlockState().getValue(ProjectorBlock.FACING);
            ProjectorKind kind = blockEntity.getProjectorKind();
            float rotation = switch (facing) {
                case SOUTH -> 180.0F;
                case EAST -> -90.0F;
                case WEST -> 90.0F;
                default -> 0.0F;
            };

            if (kind == ProjectorKind.MINIATURE) {
                int spanX = Math.max(1, bounds.getXSpan());
                int spanZ = Math.max(1, bounds.getZSpan());
                float miniatureScale = blockEntity.getMiniatureScale();
                float scale = MINIATURE_FILL / Math.max(spanX, spanZ) * miniatureScale;
                double centerX = (bounds.minX() + bounds.maxX() + 1) * 0.5D;
                double centerY = bounds.minY();
                double centerZ = (bounds.minZ() + bounds.maxZ() + 1) * 0.5D;
                return new RenderLayout(
                    kind,
                    new Vec3(0.5D, MINIATURE_Y_OFFSET, 0.5D),
                    new Vec3(-centerX, -centerY, -centerZ),
                    scale,
                    rotation,
                    bounds,
                    tint(blockEntity, 0.76F), tint(blockEntity, 0.96F), tint(blockEntity, 1.00F),
                    scale);
            }

            BlockPos anchor = blockEntity.getProjectionAnchor();
            Vec3 offset = new Vec3(
                anchor.getX() - blockEntity.getBlockPos().getX(),
                anchor.getY() - blockEntity.getBlockPos().getY(),
                anchor.getZ() - blockEntity.getBlockPos().getZ());
            return new RenderLayout(
                kind,
                offset,
                Vec3.ZERO,
                1.0F,
                rotation,
                bounds,
                tint(blockEntity, 0.72F), tint(blockEntity, 0.88F), tint(blockEntity, 1.00F),
                1.0F);
        }

        Vec3 localPointFor(Vec3 scenePoint) {
            Vec3 translated = scenePoint.add(sceneTranslate);
            double scaledX = translated.x * scale;
            double scaledY = translated.y * scale;
            double scaledZ = translated.z * scale;
            Vec3 rotated = rotateY(new Vec3(scaledX, scaledY, scaledZ), rotationDegrees);
            return origin.add(rotated);
        }

        float cardRise() {
            return kind == ProjectorKind.MINIATURE ? MINIATURE_CARD_RISE : LIFE_SIZE_CARD_RISE;
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

        private static Vec3 rotateY(Vec3 vec, float rotationDegrees) {
            double radians = Math.toRadians(rotationDegrees);
            double sin = Math.sin(radians);
            double cos = Math.cos(radians);
            double x = vec.x * cos + vec.z * sin;
            double z = vec.z * cos - vec.x * sin;
            return new Vec3(x, vec.y, z);
        }

        private static float tint(ProjectorBlockEntity blockEntity, float tintedValue) {
            return blockEntity.showBlueTint() ? tintedValue : 1.0F;
        }
    }

    record DeferredOverlayBatch(PoseSnapshot poseSnapshot, List<DeferredOverlay> overlays) {
        static DeferredOverlayBatch empty() {
            return new DeferredOverlayBatch(PoseSnapshot.identity(), List.of());
        }

        static DeferredOverlayBatch of(PoseSnapshot poseSnapshot, List<DeferredOverlay> overlays) {
            if (overlays == null || overlays.isEmpty()) {
                return empty();
            }
            return new DeferredOverlayBatch(poseSnapshot, List.copyOf(overlays));
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
            return new DeferredOverlayBatch(first.poseSnapshot(), List.copyOf(combined));
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
        static DeferredOverlay pointer(Vec3 anchor, Vec3 localPos, int accentColor) {
            return new DeferredOverlay(Kind.POINTER, localPos, anchor, List.of(), accentColor, "",
                null, 1.0F, null, null, null, ItemStack.EMPTY, null);
        }

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
            POINTER,
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
