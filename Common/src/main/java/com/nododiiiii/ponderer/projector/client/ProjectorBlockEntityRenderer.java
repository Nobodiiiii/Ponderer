package com.nododiiiii.ponderer.projector.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nododiiiii.ponderer.projector.ProjectorBlock;
import com.nododiiiii.ponderer.projector.ProjectorBlockEntity;
import com.nododiiiii.ponderer.projector.ProjectorKind;
import net.createmod.catnip.impl.client.render.ColoringVertexConsumer;
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

public class ProjectorBlockEntityRenderer implements BlockEntityRenderer<ProjectorBlockEntity> {

    private static final float MINIATURE_FILL = 0.85F;
    private static final float MINIATURE_Y_OFFSET = 1.06F;
    private static final float MINIATURE_CARD_RISE = 0.22F;
    private static final float LIFE_SIZE_CARD_RISE = 0.85F;

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
        renderProjectedScene(prepared.activeScene(), layout, poseStack, partialTick);
        renderOverlayCues(prepared.bundle().activeCues(prepared.globalTick()), layout, poseStack, bufferSource);
    }

    private void renderProjectedScene(PonderScene scene, RenderLayout layout, PoseStack poseStack, float partialTick) {
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(layout.redTint(), layout.greenTint(), layout.blueTint(), layout.alphaTint());

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
            layout.blueTint(),
            layout.alphaTint());
        scene.renderScene(projectedBuffer, graphics, partialTick);
        sceneBuffer.draw();

        poseStack.popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void renderOverlayCues(List<ProjectorSceneBundle.OverlayCue> cues, RenderLayout layout,
                                   PoseStack poseStack, MultiBufferSource bufferSource) {
        if (cues.isEmpty()) {
            return;
        }

        for (ProjectorSceneBundle.OverlayCue cue : cues) {
            Vec3 anchor = layout.localPointFor(cue.point());
            Vec3 cardPos = anchor.add(0.0D, layout.cardRise(), 0.0D);

            drawPointerLine(anchor, cardPos, poseStack, bufferSource, cue.accentColor());
            drawBillboardCard(cue.lines(), cardPos, cue.accentColor(), poseStack, bufferSource);
        }
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

    @Override
    public boolean shouldRenderOffScreen(ProjectorBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public boolean shouldRender(ProjectorBlockEntity blockEntity, Vec3 cameraPos) {
        if (blockEntity.getAnchorPos() == null) {
            return cameraPos.closerThan(Vec3.atCenterOf(blockEntity.getBlockPos()), getViewDistance());
        }
        return cameraPos.closerThan(Vec3.atCenterOf(blockEntity.getBlockPos()), getViewDistance())
            || cameraPos.closerThan(Vec3.atCenterOf(blockEntity.getAnchorPos()), getViewDistance());
    }

    private record RenderLayout(ProjectorKind kind, Vec3 origin, Vec3 sceneTranslate, float scale,
                                float rotationDegrees, BoundingBox bounds,
                                float redTint, float greenTint, float blueTint, float alphaTint) {
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
                    0.76F, 0.96F, 1.00F, 0.88F);
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
                0.72F, 0.88F, 1.00F, 0.56F);
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

        private static Vec3 rotateY(Vec3 vec, float rotationDegrees) {
            double radians = Math.toRadians(rotationDegrees);
            double sin = Math.sin(radians);
            double cos = Math.cos(radians);
            double x = vec.x * cos + vec.z * sin;
            double z = vec.z * cos - vec.x * sin;
            return new Vec3(x, vec.y, z);
        }
    }

    private record ProjectedRenderTypeBuffer(DefaultSuperRenderTypeBuffer delegate, float red, float green,
                                             float blue, float alpha) implements SuperRenderTypeBuffer {

        @Override
        public VertexConsumer getEarlyBuffer(RenderType type) {
            return tinted(delegate.getEarlyBuffer(projectedLayer(type)));
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return tinted(delegate.getBuffer(projectedLayer(type)));
        }

        @Override
        public VertexConsumer getLateBuffer(RenderType type) {
            return tinted(delegate.getLateBuffer(projectedLayer(type)));
        }

        @Override
        public void draw() {
            delegate.draw();
        }

        @Override
        public void draw(RenderType type) {
            delegate.draw(projectedLayer(type));
        }

        private VertexConsumer tinted(VertexConsumer consumer) {
            return new ColoringVertexConsumer(consumer, red, green, blue, alpha);
        }

        private static RenderType projectedLayer(RenderType type) {
            return RenderType.chunkBufferLayers().contains(type) ? RenderType.translucent() : type;
        }
    }
}
