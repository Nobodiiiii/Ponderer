package com.nododiiiii.ponderer.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.nododiiiii.ponderer.ponder.ExtraStructurePlanner;
import com.nododiiiii.ponderer.ponder.ExtraStructurePlanner.PlacedBlock;
import net.createmod.catnip.client.render.model.BakedModelBufferer;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.catnip.render.SuperByteBufferBuilder;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a slowly-rotating preview of an .nbt structure inside a Screen.
 *
 * Caching: on selection change, blocks are read via {@link ExtraStructurePlanner}, written into a
 * {@link SchematicLevel}, and baked once per {@link RenderType} into {@link SuperByteBuffer}.
 * Each frame only flushes the cached buffers — zero per-frame baking cost.
 */
public class StructurePreviewWidget {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int FULL_BRIGHT = 0xF000F0;
    private static final Vector3f DIFFUSE_LIGHT_0 = new Vector3f(-0.2f, 1.0f, 0.7f).normalize();
    private static final Vector3f DIFFUSE_LIGHT_1 = new Vector3f(0.2f, 1.0f, -0.7f).normalize();

    /** Rotation rate in degrees per second. One revolution in 60 seconds. */
    private static final float YAW_DEG_PER_SECOND = 6.0f;
    /** Fixed pitch giving a turntable-ish angle. */
    private static final float PITCH_DEG = -25.0f;

    /**
     * Pose-stack Z applied via {@code pose.translate(..., MODEL_VIEW_Z)}.
     *
     * Combined with Mojang's implicit {@code RenderSystem.modelViewMatrix} translate
     * (z = -10000), this lands the model's view-space center at ≈ -5000 and its NDC z near -0.2.
     * The picker's panel background ({@code graphics.fill}) writes depth at the GUI's default
     * pose z=0 → NDC z = +0.8; leaving the model at pose z=0 would put it at the same NDC z as
     * the background and half the fragments would tie-fail the LEQUAL depth test, cutting the
     * structure across its midline (the bug PonderUI exhibits in extreme zoom/move, too).
     * A positive pose-z pushes the model toward the camera, well in front of the background.
     */
    private static final float MODEL_VIEW_Z = 5000.0f;

    public static final int DEFAULT_LARGE_THRESHOLD = 8000;

    private int x;
    private int y;
    private int w;
    private int h;

    @Nullable
    private SchematicLevel level;
    private final Map<RenderType, SuperByteBuffer> cache = new LinkedHashMap<>();
    private double centerX, centerY, centerZ;
    private double scale = 1.0;

    private final long startMillis = System.currentTimeMillis();

    @Nullable
    private String statusKey;

    public StructurePreviewWidget(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        if (level != null) {
            recomputeScale();
        }
    }

    public void clear() {
        dispose();
        statusKey = null;
    }

    public void setStatus(@Nullable String translationKey) {
        dispose();
        this.statusKey = translationKey;
    }

    /** Load + bake. Returns the number of placed blocks (0 on failure). */
    public int load(Path nbtFile) {
        dispose();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            statusKey = "ponderer.ui.structure_picker.preview.unavailable";
            return 0;
        }

        List<PlacedBlock> placed;
        try {
            placed = ExtraStructurePlanner.plan(nbtFile, BlockPos.ZERO, 0, true);
        } catch (Exception e) {
            LOGGER.warn("StructurePreview: failed to read {}", nbtFile, e);
            statusKey = "ponderer.ui.structure_picker.preview.load_failed";
            return 0;
        }
        if (placed.isEmpty()) {
            statusKey = "ponderer.ui.structure_picker.preview.empty";
            return 0;
        }

        try {
            bake(mc, placed);
            statusKey = null;
        } catch (Exception e) {
            LOGGER.warn("StructurePreview: bake failed for {}", nbtFile, e);
            dispose();
            statusKey = "ponderer.ui.structure_picker.preview.load_failed";
        }
        return placed.size();
    }

    /**
     * Pre-count how many blocks the .nbt would produce — used by the picker to decide whether
     * to prompt before baking a huge structure.
     */
    public static int countBlocks(Path nbtFile) {
        try {
            return ExtraStructurePlanner.plan(nbtFile, BlockPos.ZERO, 0, true).size();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void bake(Minecraft mc, List<PlacedBlock> placed) {
        SchematicLevel sl = new SchematicLevel(mc.level);
        sl.renderMode = true;
        for (PlacedBlock pb : placed) {
            sl.setBlock(pb.pos, pb.state, 3);
            if (pb.nbt != null && !pb.nbt.isEmpty()) {
                BlockEntity be = sl.getBlockEntity(pb.pos);
                if (be != null) {
                    try {
                        CompoundTag saved = be.saveWithFullMetadata();
                        saved.merge(pb.nbt.copy());
                        be.load(saved);
                    } catch (Exception e) {
                        LOGGER.debug("StructurePreview: failed to apply BE nbt at {}", pb.pos, e);
                    }
                }
            }
        }
        this.level = sl;
        recomputeScale();

        // Bake all layers in one pass; route by RenderType into per-layer builders.
        Map<RenderType, SuperByteBufferBuilder> builders = new LinkedHashMap<>();
        for (RenderType rt : RenderType.chunkBufferLayers()) {
            SuperByteBufferBuilder b = new SuperByteBufferBuilder();
            b.prepare();
            builders.put(rt, b);
        }

        List<BlockPos> positions = new ArrayList<>(placed.size());
        for (PlacedBlock pb : placed) {
            positions.add(pb.pos);
        }

        BakedModelBufferer.bufferBlocks(positions.iterator(), sl, null, true,
            (renderType, shaded, data) -> {
                SuperByteBufferBuilder b = builders.get(renderType);
                if (b != null) {
                    b.add(data, shaded);
                }
            });

        for (Map.Entry<RenderType, SuperByteBufferBuilder> e : builders.entrySet()) {
            SuperByteBuffer sbb = e.getValue().build();
            if (sbb.isEmpty()) {
                sbb.delete();
            } else {
                cache.put(e.getKey(), sbb);
            }
        }
    }

    private void recomputeScale() {
        if (level == null) return;
        BoundingBox b = level.getBounds();
        int sx = b.maxX() - b.minX() + 1;
        int sy = b.maxY() - b.minY() + 1;
        int sz = b.maxZ() - b.minZ() + 1;
        centerX = (b.minX() + b.maxX()) / 2.0 + 0.5;
        centerY = (b.minY() + b.maxY()) / 2.0 + 0.5;
        centerZ = (b.minZ() + b.maxZ()) / 2.0 + 0.5;
        // Diagonal of the AABB; 0.45 leaves a 10% margin after the rotation sweeps the corners around.
        double diag = Math.sqrt((double) sx * sx + (double) sy * sy + (double) sz * sz);
        if (diag < 1.0) diag = 1.0;
        scale = Math.min(w, h) * 0.45 / diag;
    }

    public void render(GuiGraphics graphics, float partialTicks) {
        renderBackground(graphics);
        if (level == null || cache.isEmpty()) {
            renderStatus(graphics);
            return;
        }
        renderScene(graphics, partialTicks);
    }

    private void renderBackground(GuiGraphics graphics) {
        graphics.fill(x, y, x + w, y + h, 0x40000000);
        int border = 0x60FFFFFF;
        graphics.fill(x, y, x + w, y + 1, border);
        graphics.fill(x, y + h - 1, x + w, y + h, border);
        graphics.fill(x, y, x + 1, y + h, border);
        graphics.fill(x + w - 1, y, x + w, y + h, border);
    }

    private void renderStatus(GuiGraphics graphics) {
        String key = statusKey != null ? statusKey : "ponderer.ui.structure_picker.preview.placeholder";
        Component text = Component.translatable(key);
        Minecraft mc = Minecraft.getInstance();
        int tw = mc.font.width(text);
        graphics.drawString(mc.font,
            text,
            x + (w - tw) / 2,
            y + (h - mc.font.lineHeight) / 2,
            0xAACCCCCC,
            false);
    }

    private void renderScene(GuiGraphics graphics, float partialTicks) {
        PoseStack pose = graphics.pose();
        SuperRenderTypeBuffer buffer = DefaultSuperRenderTypeBuffer.getInstance();

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.setupLevelDiffuseLighting(DIFFUSE_LIGHT_0, DIFFUSE_LIGHT_1, pose.last().pose());

        pose.pushPose();
        // Park the model deep in Mojang's GUI ortho visible window (view z ∈ [-11000, -1000])
        // — see MODEL_VIEW_Z comment for the diagnostic that pinpointed this. No projection
        // surgery needed; ortho is z-position-independent for visual scale.
        //
        // Transform chain mirrors PonderScene.SceneTransform.apply (vertex pipeline runs
        // innermost-first: T_(-center) → S → flipForGuiRender → R_yaw(Y) → R_pitch(X) → T_panel).
        pose.translate(x + w / 2.0, y + h / 2.0, MODEL_VIEW_Z);
        pose.mulPose(Axis.XP.rotationDegrees(PITCH_DEG));
        float yawDeg = (((System.currentTimeMillis() - startMillis) / 1000.0f) * YAW_DEG_PER_SECOND
            + partialTicks * (YAW_DEG_PER_SECOND / 20.0f)) % 360.0f;
        pose.mulPose(Axis.YP.rotationDegrees(yawDeg));
        UIRenderHelper.flipForGuiRender(pose);
        pose.scale((float) scale, (float) scale, (float) scale);
        pose.translate(-centerX, -centerY, -centerZ);

        for (Map.Entry<RenderType, SuperByteBuffer> e : cache.entrySet()) {
            e.getValue()
                .light(FULL_BRIGHT)
                .renderInto(pose, buffer.getBuffer(e.getKey()));
        }

        renderBlockEntities(pose, buffer, partialTicks);

        buffer.draw();
        pose.popPose();

        RenderSystem.disableBlend();
    }

    private void renderBlockEntities(PoseStack pose, SuperRenderTypeBuffer buffer, float partialTicks) {
        if (level == null) return;
        BlockEntityRenderDispatcher dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
        for (BlockEntity be : level.getRenderedBlockEntities()) {
            BlockEntityRenderer<BlockEntity> renderer = dispatcher.getRenderer(be);
            if (renderer == null) continue;
            BlockPos p = be.getBlockPos();
            pose.pushPose();
            pose.translate(p.getX(), p.getY(), p.getZ());
            try {
                renderer.render(be, partialTicks, pose, buffer, FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            } catch (Exception ex) {
                LOGGER.debug("StructurePreview: BE render failed at {}", p, ex);
            }
            pose.popPose();
        }
    }

    public void dispose() {
        cache.values().forEach(SuperByteBuffer::delete);
        cache.clear();
        level = null;
    }
}
