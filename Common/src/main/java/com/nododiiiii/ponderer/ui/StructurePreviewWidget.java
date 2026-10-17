package com.nododiiiii.ponderer.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.nododiiiii.ponderer.mixin.RenderSystemShaderLightsAccessor;
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
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
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
    // Match PonderUI's lighting: GUI rendering flips the model's Y axis.
    private static final Vector3f DIFFUSE_LIGHT_0 = new Vector3f(0.4f, -1.0f, 0.7f).normalize();
    private static final Vector3f DIFFUSE_LIGHT_1 = new Vector3f(-0.4f, -0.5f, 0.7f).normalize();

    /** Rotation rate in degrees per second. One revolution in 60 seconds. */
    private static final float YAW_DEG_PER_SECOND = 6.0f;
    /** Fixed pitch giving a turntable-ish angle. */
    private static final float PITCH_DEG = -25.0f;
    /** Initial yaw offset — face the structure 180° from the auto-rotation origin. */
    private static final float INITIAL_YAW_DEG = 180.0f;
    /** Drag sensitivity in degrees per pixel. */
    private static final float DRAG_YAW_PER_PIXEL = 0.6f;
    private static final float DRAG_PITCH_PER_PIXEL = 0.4f;
    private static final float MIN_PITCH = -89.0f;
    private static final float MAX_PITCH = 89.0f;

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

    private long startMillis = System.currentTimeMillis();

    /** Extra yaw accumulated from drag, on top of the time-based auto-rotation. */
    private float dragYawDeg = 0.0f;
    /** Pitch offset on top of {@link #PITCH_DEG}, accumulated from drag. */
    private float dragPitchDeg = 0.0f;
    /** When true, auto-rotation is paused (mouse held). The last computed yaw is frozen in {@link #lockedYawDeg}. */
    private boolean rotationLocked;
    private float lockedYawDeg;
    private double lastDragX, lastDragY;

    @Nullable
    private String statusKey;
    private boolean showDefaultPlaceholder = true;

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

    public void setShowDefaultPlaceholder(boolean showDefaultPlaceholder) {
        this.showDefaultPlaceholder = showDefaultPlaceholder;
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

    /** Preview an untrusted world-generation template without placing it in the real world. */
    public void loadTemplate(CompoundTag nbt) {
        dispose();
        String statusPrefix = "ponderer.ui.structure_list.preview.";
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                setStatus(statusPrefix + "unavailable");
                return;
            }
            ListTag size = nbt.getList("size", Tag.TAG_INT);
            if (size.size() != 3) throw new IllegalArgumentException("Missing template size");
            for (int i = 0; i < 3; i++) {
                if (size.getInt(i) <= 0 || size.getInt(i) > 512) {
                    setStatus(statusPrefix + "too_large");
                    return;
                }
            }
            // Some templates offer multiple palettes; preview the first variant.
            if (!nbt.contains("palette", Tag.TAG_LIST)) {
                ListTag palettes = nbt.getList("palettes", Tag.TAG_LIST);
                if (!palettes.isEmpty()) nbt.put("palette", palettes.getList(0));
            }
            ListTag palette = nbt.getList("palette", Tag.TAG_COMPOUND);
            ListTag blocks = nbt.getList("blocks", Tag.TAG_COMPOUND);
            if (palette.isEmpty() || blocks.isEmpty()) {
                setStatus(statusPrefix + "no_template");
                return;
            }
            if (palette.size() > 8192 || blocks.size() > 131072) {
                setStatus(statusPrefix + "too_large");
                return;
            }
            for (int i = 0; i < blocks.size(); i++) {
                CompoundTag block = blocks.getCompound(i);
                ListTag pos = block.getList("pos", Tag.TAG_INT);
                int state = block.getInt("state");
                if (pos.size() != 3 || state < 0 || state >= palette.size()) {
                    throw new IllegalArgumentException("Invalid template block");
                }
                for (int axis = 0; axis < 3; axis++) {
                    if (pos.getInt(axis) < 0 || pos.getInt(axis) >= size.getInt(axis)) {
                        throw new IllegalArgumentException("Template block outside bounds");
                    }
                }
            }
            List<PlacedBlock> placed = finishTemplateBlocks(mc,
                ExtraStructurePlanner.plan(nbt, BlockPos.ZERO, 0, true));
            if (placed.isEmpty()) {
                setStatus(statusPrefix + "no_template");
            } else if (placed.size() > DEFAULT_LARGE_THRESHOLD) {
                setStatus(statusPrefix + "too_large");
            } else {
                bake(mc, placed);
                statusKey = null;
            }
        } catch (Exception | LinkageError e) {
            LOGGER.debug("StructurePreview: invalid or unsupported world-generation template", e);
            setStatus(statusPrefix + "unavailable");
        }
    }

    /** Match vanilla jigsaw final_state replacement without running world-generation processors. */
    private static List<PlacedBlock> finishTemplateBlocks(Minecraft mc, List<PlacedBlock> blocks) {
        List<PlacedBlock> result = new ArrayList<>(blocks.size());
        Map<String, BlockState> replacements = new LinkedHashMap<>();
        for (PlacedBlock block : blocks) {
            BlockState state = block.state;
            CompoundTag blockNbt = block.nbt;
            if (state.is(Blocks.JIGSAW)) {
                String finalState = blockNbt == null ? "" : blockNbt.getString("final_state");
                if (finalState.isBlank()) continue;
                state = replacements.get(finalState);
                if (state == null) {
                    try {
                        state = BlockStateParser.parseForBlock(
                            mc.level.holderLookup(Registries.BLOCK), finalState, true).blockState();
                    } catch (Exception | LinkageError e) {
                        LOGGER.debug("StructurePreview: invalid jigsaw final_state '{}'", finalState, e);
                        state = Blocks.AIR.defaultBlockState();
                    }
                    replacements.put(finalState, state);
                }
                // Replacement blocks must not inherit the jigsaw block entity's metadata.
                blockNbt = null;
            }
            if (state.isAir() || state.is(Blocks.STRUCTURE_VOID)
                || state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.JIGSAW)) continue;
            result.add(new PlacedBlock(block.pos, state, blockNbt));
        }
        return result;
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
                        CompoundTag saved = be.saveWithFullMetadata(sl.registryAccess());
                        saved.merge(pb.nbt.copy());
                        be.loadWithComponents(saved, sl.registryAccess());
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
        // The model rotates only around Y at a fixed pitch, so the worst-case projected extent
        // is much smaller than the 3D diagonal. Compute the actual rotation-swept bounds:
        //   horizontal swept = √(sx² + sz²)
        //   vertical swept   = sy·cos(pitch) + horizontalSwept·sin(|pitch|)
        // Fit each axis independently and pick the tighter constraint. 0.95 fill ⇒ ~2.5% margin.
        double horizontalSwept = Math.sqrt((double) sx * sx + (double) sz * sz);
        if (horizontalSwept < 1.0) horizontalSwept = 1.0;
        double pitchRad = Math.toRadians(Math.abs(PITCH_DEG));
        double verticalSwept = sy * Math.cos(pitchRad) + horizontalSwept * Math.sin(pitchRad);
        if (verticalSwept < 1.0) verticalSwept = 1.0;
        double fill = 0.95;
        double scaleByWidth = w * fill / horizontalSwept;
        double scaleByHeight = h * fill / verticalSwept;
        scale = Math.min(scaleByWidth, scaleByHeight);
    }

    public void render(GuiGraphics graphics, float partialTicks) {
        renderBackground(graphics);
        if (level == null || cache.isEmpty()) {
            renderStatus(graphics);
            return;
        }
        try {
            renderScene(graphics, partialTicks);
        } catch (Exception | LinkageError e) {
            LOGGER.debug("StructurePreview: render failed", e);
            setStatus("ponderer.ui.structure_picker.preview.load_failed");
            renderStatus(graphics);
        }
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
        if (statusKey == null && !showDefaultPlaceholder) {
            return;
        }
        String key = statusKey != null ? statusKey : "ponderer.ui.structure_picker.preview.placeholder";
        Component text = Component.translatable(key);
        Minecraft mc = Minecraft.getInstance();
        var lines = mc.font.split(text, Math.max(1, w - 16));
        int top = y + (h - lines.size() * mc.font.lineHeight) / 2;
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            graphics.drawString(mc.font, line, x + (w - mc.font.width(line)) / 2,
                top + i * mc.font.lineHeight, 0xAACCCCCC, false);
        }
    }

    private void renderScene(GuiGraphics graphics, float partialTicks) {
        // Isolate the GUI's pose stack from third-party block entity renderers.
        PoseStack pose = new PoseStack();
        pose.last().pose().set(graphics.pose().last().pose());
        pose.last().normal().set(graphics.pose().last().normal());
        SuperRenderTypeBuffer buffer = DefaultSuperRenderTypeBuffer.getInstance();

        graphics.flush();
        Vector3f[] lightDirections = RenderSystemShaderLightsAccessor.ponderer$getShaderLightDirections();
        Vector3f savedLight0 = new Vector3f(lightDirections[0]);
        Vector3f savedLight1 = new Vector3f(lightDirections[1]);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.setupLevelDiffuseLighting(DIFFUSE_LIGHT_0, DIFFUSE_LIGHT_1);

        pose.pushPose();
        try {
        // Park the model deep in Mojang's GUI ortho visible window (view z ∈ [-11000, -1000])
        // — see MODEL_VIEW_Z comment for the diagnostic that pinpointed this. No projection
        // surgery needed; ortho is z-position-independent for visual scale.
        //
        // Transform chain mirrors PonderScene.SceneTransform.apply (vertex pipeline runs
        // innermost-first: T_(-center) → S → flipForGuiRender → R_yaw(Y) → R_pitch(X) → T_panel).
        pose.translate(x + w / 2.0, y + h / 2.0, MODEL_VIEW_Z);
        pose.mulPose(Axis.XP.rotationDegrees(PITCH_DEG + dragPitchDeg));
        float yawDeg = rotationLocked
            ? (lockedYawDeg + dragYawDeg) % 360.0f
            : computeAutoYawDeg(partialTicks);
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
        } finally {
            pose.popPose();
            RenderSystem.setShaderLights(savedLight0, savedLight1);
            RenderSystem.disableBlend();
        }
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
            } catch (Exception | LinkageError ex) {
                LOGGER.debug("StructurePreview: BE render failed at {}", p, ex);
            } finally {
                pose.popPose();
            }
        }
    }

    public void dispose() {
        for (SuperByteBuffer buffer : cache.values()) {
            try {
                buffer.delete();
            } catch (Exception | LinkageError e) {
                LOGGER.debug("StructurePreview: failed to release preview buffer", e);
            }
        }
        cache.clear();
        level = null;
        dragYawDeg = 0.0f;
        dragPitchDeg = 0.0f;
        rotationLocked = false;
        startMillis = System.currentTimeMillis();
    }

    public boolean isInside(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** Begin drag: freeze the auto-rotation at its current angle so the drag continues from where it visually is. */
    public boolean onMouseDown(double mouseX, double mouseY) {
        if (level == null || cache.isEmpty()) return false;
        if (!isInside(mouseX, mouseY)) return false;
        lockedYawDeg = computeAutoYawDeg(0.0f);
        rotationLocked = true;
        lastDragX = mouseX;
        lastDragY = mouseY;
        return true;
    }

    public boolean onMouseDrag(double mouseX, double mouseY) {
        if (!rotationLocked) return false;
        double dx = mouseX - lastDragX;
        double dy = mouseY - lastDragY;
        lastDragX = mouseX;
        lastDragY = mouseY;
        dragYawDeg += (float) (dx * DRAG_YAW_PER_PIXEL);
        dragPitchDeg -= (float) (dy * DRAG_PITCH_PER_PIXEL);
        if (dragPitchDeg < MIN_PITCH - PITCH_DEG) dragPitchDeg = MIN_PITCH - PITCH_DEG;
        if (dragPitchDeg > MAX_PITCH - PITCH_DEG) dragPitchDeg = MAX_PITCH - PITCH_DEG;
        return true;
    }

    /** Release drag: resume auto-rotation seamlessly from the current visual yaw. */
    public boolean onMouseUp() {
        if (!rotationLocked) return false;
        // Re-anchor startMillis so that computeAutoYawDeg(0) at this instant equals (lockedYawDeg + dragYawDeg),
        // then fold the offset into the time anchor and reset the drag accumulator. This avoids a visual jump.
        float resumedYaw = (lockedYawDeg + dragYawDeg) % 360.0f;
        startMillis = System.currentTimeMillis()
            - (long) ((resumedYaw - INITIAL_YAW_DEG) / YAW_DEG_PER_SECOND * 1000.0f);
        dragYawDeg = 0.0f;
        rotationLocked = false;
        return true;
    }

    private float computeAutoYawDeg(float partialTicks) {
        return (INITIAL_YAW_DEG
            + ((System.currentTimeMillis() - startMillis) / 1000.0f) * YAW_DEG_PER_SECOND
            + partialTicks * (YAW_DEG_PER_SECOND / 20.0f)) % 360.0f;
    }
}
