package com.nododiiiii.ponderer.runtime.core;

import com.nododiiiii.ponderer.runtime.api.SceneItemBindings;
import com.nododiiiii.ponderer.scene.DslScene;
import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.runtime.mixin.TickingInstructionAccessor;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderSceneBuilder;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.createmod.ponder.foundation.instruction.PonderInstruction;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.nododiiiii.ponderer.runtime.spi.*;
import com.nododiiiii.ponderer.runtime.api.CompilationResult;
import com.nododiiiii.ponderer.runtime.api.PlaybackExecutionContext;

public final class SceneCompiler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final String namespace;
    private final StructureSource structures;
    private final SceneStepExecutor executor;
    private final java.util.function.BooleanSupplier defaultEditable;
    public SceneCompiler(String namespace, StructureSource structures, IngredientResolver ingredients, java.util.function.BooleanSupplier defaultEditable) {
        this.namespace = namespace;
        this.structures = structures;
        this.executor = new SceneStepExecutor(structures, ingredients);
        this.defaultEditable = defaultEditable;
    }

    public String getModId() {
        return namespace;
    }

    List<ResourceLocation> resolveSceneSchematics(DslScene scene, List<DslScene.SceneSegment> sceneList) {
        List<ResourceLocation> resolved = new ArrayList<>();
        ResourceLocation current = resolveDefaultSchematic(scene);

        for (int i = 0; i < sceneList.size(); i++) {
            DslScene.SceneSegment sc = sceneList.get(i);
            String explicit = extractExplicitStructureRef(sc);
            if (explicit != null) {
                ResourceLocation next = resolveStructureReference(scene, explicit);
                if (next != null) {
                    current = next;
                } else {
                    LOGGER.warn("Invalid show_structure.structure '{}' in scene {} segment {}", explicit, scene.id, i + 1);
                }
            }
            resolved.add(current);
        }

        return resolved;
    }

    private ResourceLocation resolveDefaultSchematic(DslScene scene) {
        List<String> pool = getStructurePool(scene);
        if (!pool.isEmpty()) {
            ResourceLocation fromPool = resolveSchematic(pool.get(0));
            if (fromPool != null) {
                return fromPool;
            }
        }
        return ResourceLocation.fromNamespaceAndPath("ponder", "debug/scene_1");
    }

    private List<String> getStructurePool(DslScene scene) {
        if (scene.structures != null && !scene.structures.isEmpty()) {
            return scene.structures;
        }
        if (scene.structure != null && !scene.structure.isBlank()) {
            return List.of(scene.structure);
        }
        return List.of();
    }

    private String extractExplicitStructureRef(DslScene.SceneSegment sc) {
        if (sc == null || sc.steps == null) {
            return null;
        }
        for (DslScene.DslStep step : sc.steps) {
            if (step == null || step.type == null) {
                continue;
            }
            if (!"show_structure".equalsIgnoreCase(step.type)) {
                continue;
            }
            if (step.structure != null && !step.structure.isBlank()) {
                return step.structure.trim();
            }
            return null;
        }
        return null;
    }

    private ResourceLocation resolveStructureReference(DslScene scene, String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }

        List<String> pool = getStructurePool(scene);
        Integer parsed = tryParseInt(ref);
        if (parsed != null) {
            int index = -1;
            if (parsed >= 1 && parsed <= pool.size()) {
                index = parsed - 1;
            } else if (parsed >= 0 && parsed < pool.size()) {
                index = parsed;
            }
            if (index >= 0) {
                return resolveSchematic(pool.get(index));
            }
        }

        return resolveSchematic(ref);
    }

    private Integer tryParseInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private ResourceLocation resolveSchematic(String structure) {
        if (structure == null || structure.isBlank()) {
            return ResourceLocation.fromNamespaceAndPath("ponder", "debug/scene_1");
        }
        if (structure.contains(":")) {
            ResourceLocation loc = ResourceLocation.tryParse(structure);
            return loc == null ? ResourceLocation.fromNamespaceAndPath("ponder", "debug/scene_1") : loc;
        }
        return ResourceLocation.fromNamespaceAndPath("ponder", structure);
    }

    /** Compile just the edited segment, using the same schematic resolution and storyboard as playback. */


    /** Pause later instructions during a step preview without removing them from the timeline. */


    /** Capture actual instruction completion, including non-blocking effects and native fade times. */
    private static final class TimelineSceneBuilder extends PonderSceneBuilder {
        private final TimelinePlayback playback = new TimelinePlayback();
        private int stepIndex = -1;
        private int scheduledTicks;
        private int effectEndTick;

        TimelineSceneBuilder(PonderScene scene) {
            super(scene);
        }

        void beginStep() {
            stepIndex++;
            playback.steps.add(new ArrayList<>());
        }

        void finishTimeline() {
            int endTick = Math.max(scheduledTicks, effectEndTick);
            // Apply the full effect duration on every begin/replay, after native keyframe scheduling.
            super.addInstruction(new PonderInstruction() {
                @Override public void onScheduled(PonderScene scene) {
                    scene.addToSceneTime(Math.max(0, endTick - scene.getTotalTime()));
                }
                @Override public boolean isComplete() { return true; }
                @Override public void tick(PonderScene scene) {}
            });
        }

        @Override
        public void addInstruction(PonderInstruction instruction) {
            int nativeTicks = instruction instanceof TickingInstruction
                ? ((TickingInstructionAccessor) instruction).ponderer$getTotalTicks() : 1;
            int duration = Math.max(1, nativeTicks);
            effectEndTick = Math.max(effectEndTick, scheduledTicks + duration);
            if (instruction.isBlocking()) scheduledTicks += duration;
            int instructionStep = stepIndex;
            PonderInstruction tracked = new PonderInstruction() {
                private boolean started;

                @Override public boolean isBlocking() {
                    return playback.pausesStep(instructionStep) || instruction.isBlocking();
                }
                @Override public boolean isComplete() {
                    return !playback.pausesStep(instructionStep) && started && instruction.isComplete();
                }
                @Override public void onScheduled(PonderScene scene) {
                    instruction.onScheduled(scene);
                    // Even a zero-duration blocking instruction consumes a native scheduling tick.
                    if (instruction.isBlocking() && nativeTicks == 0) scene.addToSceneTime(1);
                }
                @Override public void reset(PonderScene scene) {
                    started = false;
                    instruction.reset(scene);
                }
                @Override public void tick(PonderScene scene) {
                    if (playback.pausesStep(instructionStep)) return;
                    started = true;
                    instruction.tick(scene);
                }
            };
            if (stepIndex >= 0) playback.steps.get(stepIndex).add(tracked);
            super.addInstruction(tracked);
        }
    }

    @Nullable
    public CompilationResult compile(DslScene dsl, int segmentIndex) {
        List<DslScene.SceneSegment> segments = normalizeScenes(dsl);
        if (Minecraft.getInstance().level == null || segmentIndex < 0 || segmentIndex >= segments.size()) {
            return null;
        }
        return PlaybackExecutionContext.supply(() -> {
            ResourceLocation schematic = resolveSceneSchematics(dsl, segments).get(segmentIndex);
            PonderLevel level = new PonderLevel(BlockPos.ZERO, Minecraft.getInstance().level);
            structures.loadSchematic(schematic).placeInWorld(level, BlockPos.ZERO, BlockPos.ZERO,
                new StructurePlaceSettings(), level.random, Block.UPDATE_CLIENTS);
            level.createBackup();
            ResourceLocation component = SceneItemBindings.firstItem(dsl);
            if (component == null) component = ResourceLocation.withDefaultNamespace("stick");
            PonderScene compiled = new PonderScene(level, new PonderLocalization(), getModId(), component,
                List.of(), List.of());
            List<Integer> stepTicks = new ArrayList<>();
            TimelineSceneBuilder builder = new TimelineSceneBuilder(compiled);
            createStoryBoard(dsl, segments.get(segmentIndex), segmentIndex, segments.size(), stepTicks)
                .program(builder, compiled.getSceneBuildingUtil());
            builder.finishTimeline();
            compiled.begin();
            return new CompilationResult(compiled, stepTicks, builder.playback);
        });
    }

    PonderStoryBoard createStoryBoard(DslScene scene, DslScene.SceneSegment sc, int index, int total) {
        return createStoryBoard(scene, sc, index, total, null);
    }

    PonderStoryBoard createStoryBoard(DslScene scene, DslScene.SceneSegment sc, int index, int total,
                                             @Nullable List<Integer> stepTicks) {
        BoundingBox scanBox = preScanSegmentBounds(sc);
        return (builder, util) -> {
            try {
                if (scanBox != null) {
                    builder.getScene().getWorld().getBounds().encapsulate(scanBox);
                }
                ResourceLocation baseId = ResourceLocation.tryParse(scene.id);
                String basePath = baseId == null ? "scene" : baseId.getPath();
                String scenePath = total > 1 ? basePath + "_" + sceneSuffix(sc, index) : basePath;

                String title = sc.title != null ? sc.title.resolve() : null;
                if (title == null || title.isBlank()) {
                    String sceneTitle = scene.title != null ? scene.title.resolve() : null;
                    if (sceneTitle == null || sceneTitle.isBlank()) {
                        title = scenePath;
                    } else {
                        title = total > 1 ? sceneTitle + " #" + (index + 1) : sceneTitle;
                    }
                }
                builder.title(scenePath, title);

                if (sc.steps == null) {
                    return;
                }

                SceneStepExecutor.StepContext context = new SceneStepExecutor.StepContext();
                if (firstStepIsShowInterface(sc)) {
                    builder.removeShadow();
                }

                if (!firstStepIsShowStructure(sc)) {
                    DslScene.DslStep initial = new DslScene.DslStep();
                    initial.type = "show_structure";
                    executor.applyStep(builder, util, scene, initial, context);
                    builder.idle(20);
                }

                for (DslScene.DslStep step : sc.steps) {
                    if (builder instanceof TimelineSceneBuilder editorBuilder) editorBuilder.beginStep();
                    if (stepTicks != null) {
                        int stepIndex = stepTicks.size();
                        stepTicks.add(0);
                        // onScheduled runs in schedule order, after preceding blocking durations are counted.
                        builder.addInstruction(new PonderInstruction() {
                            @Override public void onScheduled(PonderScene ps) {
                                stepTicks.set(stepIndex, ps.getTotalTime() + 1);
                            }
                            @Override public boolean isComplete() { return true; }
                            @Override public void tick(PonderScene ps) {}
                        });
                    }
                    if (step == null || step.type == null) {
                        continue;
                    }
                    if ("next_scene".equalsIgnoreCase(step.type)) {
                        continue;
                    }
                    executor.applyStep(builder, util, scene, step, context);
                }
            } catch (Exception e) {
                LOGGER.error("Error building ponder storyboard for scene {} segment {}: {}", scene.id, index, e.getMessage(), e);
            }
        };
    }

    /**
     * Pre-scan all coordinates referenced by a scene segment's steps to determine the full
     * extent of the world the storyboard will touch. The result is encapsulated into world.bounds
     * at storyboard compile time so that PonderSceneBuildingUtil snapshots (e.g. util.select.everywhere())
     * include blocks placed outside the original schematic footprint — including negative coordinates.
     * Returns null if no coordinate-bearing step is present.
     */
    private BoundingBox preScanSegmentBounds(DslScene.SceneSegment sc) {
        if (sc == null || sc.steps == null) {
            return null;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        boolean any = false;
        for (DslScene.DslStep step : sc.steps) {
            if (step == null) {
                continue;
            }
            if (step.blockPos != null && step.blockPos.size() >= 3) {
                int x = step.blockPos.get(0), y = step.blockPos.get(1), z = step.blockPos.get(2);
                if (x < minX) minX = x; if (x > maxX) maxX = x;
                if (y < minY) minY = y; if (y > maxY) maxY = y;
                if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
                any = true;
            }
            if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
                int x = step.blockPos2.get(0), y = step.blockPos2.get(1), z = step.blockPos2.get(2);
                if (x < minX) minX = x; if (x > maxX) maxX = x;
                if (y < minY) minY = y; if (y > maxY) maxY = y;
                if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
                any = true;
            }
            if ("encapsulate_bounds".equalsIgnoreCase(step.type)
                    && step.bounds != null && step.bounds.size() >= 3) {
                int bx = step.bounds.get(0), by = step.bounds.get(1), bz = step.bounds.get(2);
                if (0 < minX) minX = 0; if (bx > maxX) maxX = bx;
                if (0 < minY) minY = 0; if (by > maxY) maxY = by;
                if (0 < minZ) minZ = 0; if (bz > maxZ) maxZ = bz;
                any = true;
            }
        }
        if (!any) {
            return null;
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private String extractScenePath(String id) {
        if (id == null || id.isBlank()) {
            return "scene";
        }
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        return parsed == null ? id : parsed.getPath();
    }

    List<DslScene.SceneSegment> normalizeScenes(DslScene scene) {
        if (scene.scenes != null && !scene.scenes.isEmpty()) {
            // Ensure each segment starts with show_structure
            for (DslScene.SceneSegment seg : scene.scenes) {
                com.nododiiiii.ponderer.scene.SceneNormalizer.normalize(scene, defaultEditable.getAsBoolean());
            }
            return scene.scenes;
        }
        List<DslScene.SceneSegment> sceneList = new ArrayList<>();
        DslScene.SceneSegment fallback = new DslScene.SceneSegment();
        fallback.steps = List.of();
        sceneList.add(fallback);
        return sceneList;
    }

    String sceneSuffix(DslScene.SceneSegment sc, int index) {
        if (sc.id != null && !sc.id.isBlank()) {
            return sc.id;
        }
        return String.valueOf(index + 1);
    }

    private boolean firstStepIsShowStructure(DslScene.SceneSegment sc) {
        if (sc.steps == null) {
            return false;
        }
        for (DslScene.DslStep step : sc.steps) {
            if (step == null || step.type == null) {
                continue;
            }
            // Return whether the first meaningful step is a valid scene-start step
            return "show_structure".equalsIgnoreCase(step.type)
                    || "show_interface".equalsIgnoreCase(step.type);
        }
        return false;
    }

    private boolean firstStepIsShowInterface(DslScene.SceneSegment sc) {
        if (sc.steps == null) {
            return false;
        }
        for (DslScene.DslStep step : sc.steps) {
            if (step == null || step.type == null) {
                continue;
            }
            return "show_interface".equalsIgnoreCase(step.type);
        }
        return false;
    }

    private PonderPalette parsePalette(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "white" -> PonderPalette.WHITE;
            case "black" -> PonderPalette.BLACK;
            case "red" -> PonderPalette.RED;
            case "green" -> PonderPalette.GREEN;
            case "blue" -> PonderPalette.BLUE;
            case "input" -> PonderPalette.INPUT;
            case "output" -> PonderPalette.OUTPUT;
            case "slow" -> PonderPalette.SLOW;
            case "medium" -> PonderPalette.MEDIUM;
            case "fast" -> PonderPalette.FAST;
            default -> null;
        };
    }
}
