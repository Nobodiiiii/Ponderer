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
import org.joml.Vector4f;
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
     * View-space Z offset applied via {@code pose.translate(..., MODEL_VIEW_Z)}.
     *
     * Diagnostic value while we hunt the half-cut bug. At 0, model lands at NDC z ≈ 0.8 (per
     * diagnostic dump). Setting this to a large positive value pushes the model close to the
     * GUI's view-z origin — if the half-cut goes away here, the bug is depth-test interaction
     * with something at the original depth (probably a widget or panel-background fill writing
     * depth at view z ≈ -10000).
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
    /** Print one diagnostic dump per loaded structure; set false after first frame logs. */
    private boolean diagPending;

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
        diagPending = true;
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

        if (diagPending) {
            dumpRenderState(pose);
            diagPending = false;
        }

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
        diagPending = false;
    }

    // ──────────────────────────────────────────────────────────────────────
    // DIAGNOSTIC — prints once per load. Investigates why parts of the model
    // get clipped despite PonderUI's translate(0,0,800) trick. We dump:
    //   1. Mojang's CURRENT projection matrix (set up by GameRenderer for GUI)
    //   2. The pose-stack's last() matrix AT renderScene entry (before our
    //      transforms) — this tells us if there's an implicit ModelView
    //      pre-translate we're not accounting for.
    //   3. The matrix Mojang's setProjection would have if we read m22/m32
    //      out — from these you can solve for the actual zNear/zFar.
    //   4. A walk-through of the model-center vertex (cx,cy,cz) through:
    //      pose-stack → view space → after proj.translate(800) → clip space → NDC.
    //   5. Same walk for two opposite corners of the model bounding box so we
    //      can see which side ends up outside NDC z range [-1, +1].
    // ──────────────────────────────────────────────────────────────────────
    private void dumpRenderState(PoseStack poseAtEntry) {
        Matrix4f mojangProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4f poseEntry = new Matrix4f(poseAtEntry.last().pose());
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());

        LOGGER.info("============================================================");
        LOGGER.info("STRUCTURE PREVIEW DIAG  (panel x={} y={} w={} h={}, scale={}, center=({}, {}, {}))",
            x, y, w, h,
            String.format("%.4f", scale),
            String.format("%.3f", centerX),
            String.format("%.3f", centerY),
            String.format("%.3f", centerZ));
        LOGGER.info("[1] Mojang projection (raw, before our translate):");
        logMatrix(mojangProj);
        LOGGER.info("    Reading zNear/zFar from m22={}, m32={}:",
            mojangProj.m22(), mojangProj.m32());
        if (Math.abs(mojangProj.m22()) > 1e-9f) {
            float diff = 2.0f / mojangProj.m22();        // zNear - zFar
            float sum  = mojangProj.m32() * diff;        // zNear + zFar
            float zNear = (sum + diff) / 2.0f;
            float zFar  = (sum - diff) / 2.0f;
            LOGGER.info("    → zNear = {}, zFar = {}  (visible view z ∈ [{}, {}])",
                zNear, zFar, -zFar, -zNear);
        }

        LOGGER.info("[2a] PoseStack.last() at renderScene entry (graphics.pose()):");
        logMatrix(poseEntry);

        LOGGER.info("[2b] RenderSystem.modelViewMatrix (the matrix the shader actually applies):");
        logMatrix(modelView);
        LOGGER.info("    → implicit view-z translation = m32 = {}  (pose-z=0 → view-z = this value)",
            modelView.m32());

        // Effective transform on a vertex: ProjMat * ModelView * PoseMat * objectPos
        Matrix4f fullPose = new Matrix4f(poseEntry);
        fullPose.translate((float) (x + w / 2.0), (float) (y + h / 2.0), MODEL_VIEW_Z);
        fullPose.rotate(Axis.XP.rotationDegrees(PITCH_DEG));
        fullPose.rotate(Axis.YP.rotationDegrees(0));   // yaw=0 for diag (model snapshot)
        fullPose.mul(new Matrix4f().scaling(1, -1, 1));   // flipForGuiRender
        fullPose.scale((float) scale, (float) scale, (float) scale);
        fullPose.translate((float) -centerX, (float) -centerY, (float) -centerZ);

        Matrix4f projUsed = new Matrix4f(mojangProj);

        LOGGER.info("[3] Projection used for draw (no more proj.translate trick):");
        logMatrix(projUsed);

        BoundingBox b = level != null ? level.getBounds() : new BoundingBox(BlockPos.ZERO);
        Vector3f[] probes = new Vector3f[]{
            new Vector3f((float) centerX,      (float) centerY,      (float) centerZ),       // model center
            new Vector3f((float) (b.minX()),   (float) (b.minY()),   (float) (b.minZ())),    // min corner
            new Vector3f((float) (b.maxX()+1), (float) (b.maxY()+1), (float) (b.maxZ()+1)),  // max corner
            new Vector3f((float) (b.minX()),   (float) (b.minY()),   (float) (b.maxZ()+1)),  // mixed corner 1
            new Vector3f((float) (b.maxX()+1), (float) (b.minY()),   (float) (b.minZ())),    // mixed corner 2
        };
        String[] labels = {"center", "min-corner", "max-corner", "min-XY/max-Z", "max-X/min-YZ"};

        LOGGER.info("[4] Sample vertex transforms (yaw=0 snapshot; including ModelView):");
        for (int i = 0; i < probes.length; i++) {
            Vector4f v = new Vector4f(probes[i], 1);
            Vector4f afterPose = new Vector4f(v).mul(fullPose);
            Vector4f afterModelView = new Vector4f(afterPose).mul(modelView);
            Vector4f clip = new Vector4f(afterModelView).mul(projUsed);
            float ndcZ = clip.w() != 0 ? clip.z() / clip.w() : Float.NaN;
            float ndcX = clip.w() != 0 ? clip.x() / clip.w() : Float.NaN;
            float ndcY = clip.w() != 0 ? clip.y() / clip.w() : Float.NaN;
            String inRange = (ndcZ >= -1f && ndcZ <= 1f) ? "visible" : "CLIPPED";
            LOGGER.info("    {} (object={}) → pose=({}, {}, {})  → mv=({}, {}, {})  → ndc=({}, {}, {}) [{}]",
                String.format("%-14s", labels[i]),
                fmt(probes[i]),
                fmt3(afterPose.x()),       fmt3(afterPose.y()),       fmt3(afterPose.z()),
                fmt3(afterModelView.x()),  fmt3(afterModelView.y()),  fmt3(afterModelView.z()),
                fmt3(ndcX), fmt3(ndcY), fmt3(ndcZ),
                inRange);
        }
        LOGGER.info("============================================================");
    }

    private static void logMatrix(Matrix4f m) {
        LOGGER.info("    | {} {} {} {} |", fmt3(m.m00()), fmt3(m.m10()), fmt3(m.m20()), fmt3(m.m30()));
        LOGGER.info("    | {} {} {} {} |", fmt3(m.m01()), fmt3(m.m11()), fmt3(m.m21()), fmt3(m.m31()));
        LOGGER.info("    | {} {} {} {} |", fmt3(m.m02()), fmt3(m.m12()), fmt3(m.m22()), fmt3(m.m32()));
        LOGGER.info("    | {} {} {} {} |", fmt3(m.m03()), fmt3(m.m13()), fmt3(m.m23()), fmt3(m.m33()));
    }

    private static String fmt(Vector3f v) {
        return "(" + fmt3(v.x()) + ", " + fmt3(v.y()) + ", " + fmt3(v.z()) + ")";
    }

    private static String fmt3(float f) {
        return String.format("%12.4f", f);
    }
}
