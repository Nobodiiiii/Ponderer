package com.nododiiiii.ponderer.editor.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.nododiiiii.ponderer.runtime.mixin.RenderSystemShaderLightsAccessor;
import com.nododiiiii.ponderer.support.structure.ExtraStructurePlanner;
import com.nododiiiii.ponderer.support.structure.ExtraStructurePlanner.PlacedBlock;
import net.createmod.catnip.client.render.model.BakedModelBufferer;
import net.createmod.catnip.gui.ConfirmationScreen;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.catnip.render.SuperByteBufferBuilder;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.joml.Vector3f;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Renders a slowly-rotating preview of an .nbt structure inside a Screen.
 *
 * Reads and plans structures on background workers. Schematic population and model baking stay
 * on the render thread, split into bounded batches so loading does not monopolize a frame.
 * Once loading finishes, each frame only flushes the cached buffers.
 */
public class StructurePreviewWidget {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PICKER_STATUS = "ponderer.ui.structure_picker.preview.";
    private static final String TEMPLATE_STATUS = "ponderer.ui.structure_list.preview.";
    private static final long BUILD_BUDGET_NANOS = 2_000_000L;
    private static final int BUILD_BATCH_SIZE = 128;
    private static final ThreadPoolExecutor LOAD_EXECUTOR = new ThreadPoolExecutor(
        2, 2, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), task -> {
            Thread thread = new Thread(task, "ponderer-structure-preview");
            thread.setDaemon(true);
            return thread;
        });

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
    private final Map<RenderType, List<SuperByteBuffer>> cache = new LinkedHashMap<>();
    @Nullable private FutureTask<PreparedPreview> pendingLoad;
    @Nullable private PreviewBuild pendingBuild;
    @Nullable private ClientLevel sourceLevel;
    @Nullable private Screen confirmationParent;
    private long loadVersion;
    private String failureStatus = PICKER_STATUS + "load_failed";
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

    /** Read once in the background; large previews ask before starting the incremental build. */
    public void load(Path nbtFile, Screen parent) {
        dispose();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            statusKey = PICKER_STATUS + "unavailable";
            return;
        }
        sourceLevel = mc.level;
        confirmationParent = parent;
        failureStatus = PICKER_STATUS + "load_failed";
        statusKey = PICKER_STATUS + "loading";
        submitLoad(() -> {
            List<PlacedBlock> placed = ExtraStructurePlanner.plan(nbtFile, BlockPos.ZERO, 0, true);
            return new PreparedPreview(placed, placed.isEmpty() ? PICKER_STATUS + "empty" : null);
        });
    }

    /** Decompress and validate an untrusted template off-thread, without touching the real world. */
    public void loadTemplate(byte[] compressedTemplate) {
        dispose();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            statusKey = TEMPLATE_STATUS + "unavailable";
            return;
        }
        sourceLevel = mc.level;
        var blockLookup = sourceLevel.holderLookup(Registries.BLOCK);
        failureStatus = TEMPLATE_STATUS + "unavailable";
        statusKey = TEMPLATE_STATUS + "loading";
        submitLoad(() -> {
            try (var input = new ByteArrayInputStream(compressedTemplate)) {
                CompoundTag nbt = NbtIo.readCompressed(input, new NbtAccounter(8 * 1024 * 1024L, 64));
                return prepareTemplate(nbt, blockLookup);
            }
        });
    }

    private void submitLoad(Callable<PreparedPreview> task) {
        pendingLoad = new FutureTask<>(task);
        LOAD_EXECUTOR.execute(pendingLoad);
    }

    private static PreparedPreview prepareTemplate(CompoundTag nbt, HolderLookup<Block> blockLookup) {
        ListTag size = nbt.getList("size", Tag.TAG_INT);
        if (size.size() != 3) throw new IllegalArgumentException("Missing template size");
        for (int i = 0; i < 3; i++) {
            if (size.getInt(i) <= 0 || size.getInt(i) > 512) {
                return new PreparedPreview(List.of(), TEMPLATE_STATUS + "too_large");
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
            return new PreparedPreview(List.of(), TEMPLATE_STATUS + "no_template");
        }
        if (palette.size() > 8192 || blocks.size() > 131072) {
            return new PreparedPreview(List.of(), TEMPLATE_STATUS + "too_large");
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
        List<PlacedBlock> placed = finishTemplateBlocks(blockLookup,
            ExtraStructurePlanner.plan(nbt, BlockPos.ZERO, 0, true));
        if (placed.isEmpty()) {
            return new PreparedPreview(List.of(), TEMPLATE_STATUS + "no_template");
        } else if (placed.size() > DEFAULT_LARGE_THRESHOLD) {
            return new PreparedPreview(List.of(), TEMPLATE_STATUS + "too_large");
        }
        return new PreparedPreview(placed, null);
    }

    /** Match vanilla jigsaw final_state replacement without running world-generation processors. */
    private static List<PlacedBlock> finishTemplateBlocks(HolderLookup<Block> blockLookup, List<PlacedBlock> blocks) {
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
                            blockLookup, finalState, true).blockState();
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

    private void advanceLoading() {
        Minecraft mc = Minecraft.getInstance();
        if (sourceLevel != null && sourceLevel != mc.level) {
            setStatus(failureStatus);
            return;
        }
        if (pendingLoad == null || !pendingLoad.isDone()) return;
        // ConfirmationScreen renders its source screen as a background; wait until it is active.
        if (confirmationParent != null && mc.screen != confirmationParent) return;
        FutureTask<PreparedPreview> completed = pendingLoad;
        pendingLoad = null;
        try {
            // isDone above guarantees this does not wait on the render thread.
            PreparedPreview prepared = completed.get();
            if (prepared.statusKey != null) {
                setStatus(prepared.statusKey);
            } else if (confirmationParent != null && prepared.blocks.size() > DEFAULT_LARGE_THRESHOLD) {
                statusKey = PICKER_STATUS + "confirm_pending";
                long version = loadVersion;
                new ConfirmationScreen().centered()
                    .withText(Component.translatable(PICKER_STATUS + "large", prepared.blocks.size()))
                    .withAction(success -> {
                        if (version != loadVersion) return;
                        if (sourceLevel != Minecraft.getInstance().level) {
                            setStatus(failureStatus);
                        } else if (Boolean.TRUE.equals(success)) {
                            beginBuild(prepared.blocks);
                        } else {
                            setStatus(PICKER_STATUS + "skipped");
                        }
                    }).open(confirmationParent);
            } else {
                beginBuild(prepared.blocks);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            setStatus(failureStatus);
        } catch (ExecutionException | RuntimeException | LinkageError e) {
            LOGGER.debug("StructurePreview: asynchronous load failed", e);
            setStatus(failureStatus);
        }
    }

    private void beginBuild(List<PlacedBlock> placed) {
        level = new SchematicLevel(sourceLevel);
        level.renderMode = true;
        pendingBuild = new PreviewBuild(placed);
        statusKey = confirmationParent == null ? TEMPLATE_STATUS + "loading" : PICKER_STATUS + "loading";
    }

    private void advanceBuild() {
        if (pendingBuild == null || level == null) return;
        PreviewBuild build = pendingBuild;
        long deadline = System.nanoTime() + BUILD_BUDGET_NANOS;
        int start = build.placedIndex;
        while (build.placedIndex < build.blocks.size() && build.placedIndex - start < BUILD_BATCH_SIZE
            && (build.placedIndex == start || System.nanoTime() < deadline)) {
            PlacedBlock pb = build.blocks.get(build.placedIndex++);
            level.setBlock(pb.pos, pb.state, 3);
            if (pb.nbt != null && !pb.nbt.isEmpty()) {
                BlockEntity be = level.getBlockEntity(pb.pos);
                if (be != null) {
                    try {
                        CompoundTag saved = be.saveWithFullMetadata(level.registryAccess());
                        saved.merge(pb.nbt.copy());
                        be.loadWithComponents(saved, level.registryAccess());
                    } catch (Exception e) {
                        LOGGER.debug("StructurePreview: failed to apply BE nbt at {}", pb.pos, e);
                    }
                }
            }
        }
        // Populate all neighbors before baking; never populate and bake in the same frame.
        if (build.placedIndex != start) return;

        // Finish each small batch independently; a final whole-structure mesh copy would stall.
        Map<RenderType, SuperByteBufferBuilder> builders = new LinkedHashMap<>();
        for (RenderType rt : RenderType.chunkBufferLayers()) {
            SuperByteBufferBuilder b = new SuperByteBufferBuilder();
            b.prepare();
            builders.put(rt, b);
        }

        int batchStart = build.bakedIndex;
        Iterator<BlockPos> positions = new Iterator<>() {
            @Override public boolean hasNext() {
                return build.bakedIndex < build.blocks.size() && build.bakedIndex - batchStart < BUILD_BATCH_SIZE
                    && (build.bakedIndex == batchStart || System.nanoTime() < deadline);
            }
            @Override public BlockPos next() {
                if (build.bakedIndex >= build.blocks.size()) throw new NoSuchElementException();
                return build.blocks.get(build.bakedIndex++).pos;
            }
        };
        BakedModelBufferer.bufferBlocks(positions, level, null, true,
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
                cache.computeIfAbsent(e.getKey(), key -> new ArrayList<>()).add(sbb);
            }
        }
        if (build.bakedIndex == build.blocks.size()) {
            pendingBuild = null;
            statusKey = null;
            startMillis = System.currentTimeMillis();
            recomputeScale();
        }
    }

    private record PreparedPreview(List<PlacedBlock> blocks, @Nullable String statusKey) {}

    private static final class PreviewBuild {
        private final List<PlacedBlock> blocks;
        private int placedIndex;
        private int bakedIndex;

        private PreviewBuild(List<PlacedBlock> blocks) {
            this.blocks = blocks;
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
        try {
            advanceLoading();
            advanceBuild();
        } catch (Exception | LinkageError e) {
            LOGGER.debug("StructurePreview: incremental build failed", e);
            setStatus(failureStatus);
        }
        if (pendingBuild != null || level == null || cache.isEmpty()) {
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

        for (Map.Entry<RenderType, List<SuperByteBuffer>> e : cache.entrySet()) {
            for (SuperByteBuffer batch : e.getValue()) {
                batch.light(FULL_BRIGHT).renderInto(pose, buffer.getBuffer(e.getKey()));
            }
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
        loadVersion++;
        if (pendingLoad != null) {
            pendingLoad.cancel(true);
            LOAD_EXECUTOR.remove(pendingLoad);
            pendingLoad = null;
        }
        pendingBuild = null;
        sourceLevel = null;
        confirmationParent = null;
        for (List<SuperByteBuffer> batches : cache.values()) {
            for (SuperByteBuffer buffer : batches) {
                try {
                    buffer.delete();
                } catch (Exception | LinkageError e) {
                    LOGGER.debug("StructurePreview: failed to release preview buffer", e);
                }
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
        if (pendingBuild != null || level == null || cache.isEmpty()) return false;
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
