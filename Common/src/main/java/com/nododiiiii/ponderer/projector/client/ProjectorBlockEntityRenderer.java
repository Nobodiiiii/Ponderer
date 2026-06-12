package com.nododiiiii.ponderer.projector.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import com.nododiiiii.ponderer.mixin.InputWindowElementAccessor;
import com.nododiiiii.ponderer.mixin.TextWindowElementAccessor;
import com.nododiiiii.ponderer.projector.ProjectorBlock;
import com.nododiiiii.ponderer.projector.ProjectorBlockEntity;
import com.nododiiiii.ponderer.projector.ProjectorKind;
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
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class ProjectorBlockEntityRenderer implements BlockEntityRenderer<ProjectorBlockEntity> {

    private static final float MINIATURE_FILL = 0.85F;
    private static final float MINIATURE_Y_OFFSET = 1.06F;
    private static final float MINIATURE_CARD_RISE = 0.22F;
    private static final float LIFE_SIZE_CARD_RISE = 0.85F;
    private static final float LOCAL_OVERLAY_TEXT_Z = 0.02F;
    private static final float LOCAL_OVERLAY_ICON_Z = 0.04F;
    private static final float INPUT_SNAPSHOT_Z = LOCAL_OVERLAY_ICON_Z;

    private final InputOverlaySnapshot inputOverlaySnapshot = new InputOverlaySnapshot();

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
        renderProjectedScene(prepared.activeScene(), layout, poseStack, prepared.localTick(), partialTick);
        boolean renderedNativeOverlay = renderNativePonderOverlays(prepared.activeScene(), layout, poseStack, bufferSource,
            partialTick);
        List<ProjectorSceneBundle.OverlayCue> cues = prepared.bundle().activeCues(prepared.globalTick());
        if (!prepared.segment().extractRuntimeOverlays() || !renderedNativeOverlay) {
            renderOverlayCues(cues, layout, poseStack, bufferSource);
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

    private boolean renderNativePonderOverlays(PonderScene scene, RenderLayout layout, PoseStack poseStack,
                                               MultiBufferSource bufferSource, float partialTick) {
        Set<PonderElement> elements = scene.getElements();
        if (elements.isEmpty()) {
            return false;
        }

        boolean rendered = false;
        int fallbackLane = 0;

        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        try {
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
                        if (renderTextOverlay(accessor, fade, lane, layout, poseStack, bufferSource)) {
                            rendered = true;
                            if (accessor.ponderer$getVec() == null) {
                                fallbackLane = lane + 1;
                            }
                        }
                        continue;
                    }

                    if (element instanceof InputWindowElement inputElement) {
                        if (renderInputOverlay((InputWindowElementAccessor) inputElement, fade, layout, poseStack)) {
                            rendered = true;
                        }
                    }
                } catch (RuntimeException ignored) {
                }
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }

        return rendered;
    }

    private void renderOverlayCues(List<ProjectorSceneBundle.OverlayCue> cues, RenderLayout layout,
                                   PoseStack poseStack, MultiBufferSource bufferSource) {
        if (cues.isEmpty()) {
            return;
        }

        for (ProjectorSceneBundle.OverlayCue cue : cues) {
            try {
                if (cue.anchorMode() == ProjectorSceneBundle.OverlayCue.AnchorMode.FALLBACK) {
                    drawBillboardCard(cue.lines(), layout.fallbackCardPosition(cue.fallbackLane()),
                        cue.accentColor(), poseStack, bufferSource);
                    continue;
                }

                Vec3 anchor = layout.localPointFor(cue.point());
                Vec3 cardPos = anchor.add(0.0D, layout.cardRise(), 0.0D);

                drawPointerLine(anchor, cardPos, poseStack, bufferSource, cue.accentColor());
                drawBillboardCard(cue.lines(), cardPos, cue.accentColor(), poseStack, bufferSource);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private boolean renderTextOverlay(TextWindowElementAccessor accessor, float fade, int fallbackLane,
                                      RenderLayout layout, PoseStack poseStack, MultiBufferSource bufferSource) {
        String text = resolveText(accessor);
        if (text == null || text.isBlank()) {
            return false;
        }

        PonderPalette palette = accessor.ponderer$getPalette();
        int accentColor = palette == null ? 0xE6FCFF : palette.getColor();
        Vec3 anchorPoint = accessor.ponderer$getVec();
        Vec3 localPos;
        if (anchorPoint != null) {
            Vec3 anchor = layout.localPointFor(anchorPoint);
            localPos = anchor.add(0.0D, layout.cardRise(), 0.0D);
            drawPointerLine(anchor, localPos, poseStack, bufferSource, accentColor);
        } else {
            localPos = layout.fallbackCardPosition(fallbackLane);
        }

        drawTextWindowBillboard(text, localPos, palette, fade, poseStack, bufferSource);
        return true;
    }

    private boolean renderInputOverlay(InputWindowElementAccessor accessor, float fade,
                                       RenderLayout layout, PoseStack poseStack) {
        ScreenElement icon = accessor.ponderer$getIcon();
        ResourceLocation key = accessor.ponderer$getKey();
        String text = key == null ? "" : PonderIndex.getLangAccess().getShared(key);
        if (text == null) {
            text = "";
        }

        if (icon == null && text.isBlank() && accessor.ponderer$getItem().isEmpty()) {
            return false;
        }

        drawInputBubbleBillboard(accessor.ponderer$getSceneSpace(), accessor.ponderer$getDirection(), icon, text,
            accessor.ponderer$getItem(), fade, layout, poseStack);
        return true;
    }

    private void drawPointerLine(Vec3 anchor, Vec3 card, PoseStack poseStack,
                                 MultiBufferSource bufferSource, int color) {
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();
        consumer.vertex(matrix, (float) anchor.x, (float) anchor.y, (float) anchor.z)
            .color(red, green, blue, 0.95F)
            .normal(0.0F, 1.0F, 0.0F)
            .endVertex();
        consumer.vertex(matrix, (float) card.x, (float) card.y - 0.08F, (float) card.z)
            .color(red, green, blue, 0.75F)
            .normal(0.0F, 1.0F, 0.0F)
            .endVertex();
    }

    private void drawBillboardCard(List<Component> lines, Vec3 localPos, int accentColor,
                                   PoseStack poseStack, MultiBufferSource bufferSource) {
        if (lines.isEmpty()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        float textScale = 0.018F;
        int widest = 0;
        for (Component line : lines) {
            widest = Math.max(widest, font.width(line));
        }

        int totalHeight = lines.size() * font.lineHeight;
        int backgroundColor = 0x66000000;
        int textColor = 0xF2FFFFFF;

        poseStack.pushPose();
        poseStack.translate(localPos.x, localPos.y, localPos.z);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-textScale, -textScale, textScale);
        poseStack.translate(-widest / 2.0F, -(totalHeight + 2) / 2.0F, 0.0F);

        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            float y = i * font.lineHeight;
            int color = i == 0 ? accentColor | 0xFF000000 : textColor;
            font.drawInBatch(
                line,
                0.0F,
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
                0.0F,
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

    private void drawTextWindowBillboard(String text, Vec3 localPos, PonderPalette palette, float fade,
                                         PoseStack poseStack, MultiBufferSource bufferSource) {
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

        poseStack.pushPose();
        poseStack.translate(localPos.x, localPos.y, localPos.z);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-uiScaleFor(localPos), -uiScaleFor(localPos), uiScaleFor(localPos));
        poseStack.translate(-boxWidth / 2.0F, -(boxHeight + 6.0F) / 2.0F, 0.0F);

        GuiGraphics graphics = ProjectorGuiGraphicsBridge.create(poseStack);
        new BoxElement()
            .withBackground(PonderUI.BACKGROUND_FLAT)
            .gradientBorder(TextWindowElement.COLOR_WINDOW_BORDER)
            .at(-10, 3, 0)
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
                -10.0F,
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
                -10.0F,
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
        Vec3 localPos = layout.localPointFor(scenePoint);
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

        poseStack.pushPose();
        poseStack.translate(localPos.x, localPos.y, localPos.z);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-uiScaleFor(localPos), -uiScaleFor(localPos), uiScaleFor(localPos));
        poseStack.translate(xFade, yFade, 0.0F);

        GuiGraphics graphics = ProjectorGuiGraphicsBridge.create(poseStack);
        renderSpeechBoxLocal(graphics, 0, 0, width, height, false, direction);
        graphics.flush();

        if (inputOverlaySnapshot.render(width, height, text, keyWidth, hasIcon ? icon : null,
            hasItem ? item : ItemStack.EMPTY, fade)) {
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, INPUT_SNAPSHOT_Z);
            inputOverlaySnapshot.draw(poseStack, width, height);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    private void renderSpeechBoxLocal(GuiGraphics graphics, int x, int y, int w, int h, boolean highlighted,
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

    private float uiScaleFor(Vec3 localPos) {
        return 0.018F;
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
                                float redTint, float greenTint, float blueTint) {
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
                int spanY = Math.max(1, bounds.getYSpan());
                int spanZ = Math.max(1, bounds.getZSpan());
                float scale = MINIATURE_FILL / Math.max(spanX, Math.max(spanY, spanZ));
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
                    tint(blockEntity, 0.76F), tint(blockEntity, 0.96F), tint(blockEntity, 1.00F));
            }

            BlockPos anchor = blockEntity.getAnchorPos() == null ? blockEntity.getBlockPos() : blockEntity.getAnchorPos();
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
                tint(blockEntity, 0.72F), tint(blockEntity, 0.88F), tint(blockEntity, 1.00F));
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

    /**
     * Renders the show_controls foreground into a tiny framebuffer, then projects that texture
     * onto the already-drawn bubble background as one flat quad.
     */
    private static final class InputOverlaySnapshot {
        private TextureTarget target;

        boolean render(int width, int height, String text, int keyWidth,
                       ScreenElement icon, ItemStack item, float fade) {
            ensureTarget(width, height);
            if (target == null) {
                return false;
            }

            GlStateSnapshot glState = GlStateSnapshot.capture();
            target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            target.bindWrite(true);
            target.clear(Minecraft.ON_OSX);

            RenderSystem.backupProjectionMatrix();
            PoseStack modelView = RenderSystem.getModelViewStack();
            modelView.pushPose();
            try {
                RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(
                    0.0F, width, height, 0.0F, 1000.0F, 21000.0F),
                    VertexSorting.ORTHOGRAPHIC_Z);
                modelView.setIdentity();
                modelView.translate(0.0F, 0.0F, -11000.0F);
                RenderSystem.applyModelViewMatrix();

                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);

                PoseStack offscreenPose = new PoseStack();
                GuiGraphics graphics = ProjectorGuiGraphicsBridge.create(offscreenPose);

                offscreenPose.pushPose();
                offscreenPose.translate(0.0F, 0.0F, 100.0F);

                if (text != null && !text.isBlank()) {
                    int color = PonderPalette.WHITE.getColorObject().copy().scaleAlphaForText(fade).getRGB();
                    Font font = Minecraft.getInstance().font;
                    graphics.drawString(font, text, 2, (int) ((height - font.lineHeight) / 2.0F + 2.0F),
                        color, false);
                }

                if (icon != null) {
                    offscreenPose.pushPose();
                    offscreenPose.translate(keyWidth, 0.0F, INPUT_SNAPSHOT_Z);
                    offscreenPose.scale(1.5F, 1.5F, 1.5F);
                    icon.render(graphics, 0, 0);
                    offscreenPose.popPose();
                }

                if (item != null && !item.isEmpty()) {
                    offscreenPose.pushPose();
                    offscreenPose.translate(keyWidth + (icon != null ? 24 : 0), 0.0F, INPUT_SNAPSHOT_Z);
                    offscreenPose.scale(1.5F, 1.5F, 1.5F);
                    graphics.renderItem(item, 0, 0);
                    offscreenPose.popPose();
                    RenderSystem.disableDepthTest();
                }

                graphics.flush();
                offscreenPose.popPose();
                return true;
            } catch (RuntimeException ignored) {
                return false;
            } finally {
                modelView.popPose();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.restoreProjectionMatrix();
                glState.restore();
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }

        void draw(PoseStack poseStack, int width, int height) {
            if (target == null) {
                return;
            }

            GlStateSnapshot glState = GlStateSnapshot.capture();
            try {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
                RenderSystem.disableCull();
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                RenderSystem.setShaderTexture(0, target.getColorTextureId());
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

                Matrix4f matrix = poseStack.last().pose();
                float x0 = 0.0F;
                float y0 = 0.0F;
                float x1 = width;
                float y1 = height;
                float u1 = width / (float) target.width;
                float v1 = height / (float) target.height;

                BufferBuilder buffer = Tesselator.getInstance().getBuilder();
                buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                buffer.vertex(matrix, x0, y1, 0.0F).uv(0.0F, 0.0F).endVertex();
                buffer.vertex(matrix, x1, y1, 0.0F).uv(u1, 0.0F).endVertex();
                buffer.vertex(matrix, x1, y0, 0.0F).uv(u1, v1).endVertex();
                buffer.vertex(matrix, x0, y0, 0.0F).uv(0.0F, v1).endVertex();
                BufferUploader.drawWithShader(buffer.end());
            } finally {
                glState.restore();
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }

        private void ensureTarget(int width, int height) {
            if (target != null && target.viewWidth == width && target.viewHeight == height) {
                return;
            }

            if (target != null) {
                target.destroyBuffers();
            }
            target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            target.setFilterMode(org.lwjgl.opengl.GL11.GL_LINEAR);
        }

        private record GlStateSnapshot(int drawFramebuffer, int readFramebuffer, int[] viewport,
                                       boolean depthTest, boolean depthMask, int depthFunc,
                                       boolean blend, boolean cull) {
            static GlStateSnapshot capture() {
                int[] viewport = new int[4];
                GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
                return new GlStateSnapshot(
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                    viewport,
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE));
            }

            void restore() {
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
                RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                if (depthTest) {
                    RenderSystem.enableDepthTest();
                } else {
                    RenderSystem.disableDepthTest();
                }
                RenderSystem.depthMask(depthMask);
                RenderSystem.depthFunc(depthFunc);
                if (blend) {
                    RenderSystem.enableBlend();
                } else {
                    RenderSystem.disableBlend();
                }
                if (cull) {
                    RenderSystem.enableCull();
                } else {
                    RenderSystem.disableCull();
                }
            }
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
