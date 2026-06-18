package com.nododiiiii.ponderer.projector.client;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import com.nododiiiii.ponderer.projector.ProjectorSceneKey;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ProjectorSceneBundle {

    private static final Vec3 DEFAULT_POINT = new Vec3(2.5D, 1.5D, 2.5D);

    public record OverlayCue(int startTick, int endTick, Vec3 point, List<Component> lines, int accentColor,
                             AnchorMode anchorMode, int fallbackLane) {
        public OverlayCue(int startTick, int endTick, Vec3 point, List<Component> lines, int accentColor) {
            this(startTick, endTick, point, lines, accentColor, AnchorMode.WORLD, 0);
        }

        public static OverlayCue runtimeWorld(int localTick, Vec3 point, List<Component> lines, int accentColor) {
            return new OverlayCue(localTick, localTick + 1, point, lines, accentColor, AnchorMode.WORLD, 0);
        }

        public static OverlayCue runtimeFallback(int localTick, int fallbackLane, List<Component> lines, int accentColor) {
            return new OverlayCue(localTick, localTick + 1, DEFAULT_POINT, lines, accentColor,
                AnchorMode.FALLBACK, Math.max(0, fallbackLane));
        }

        public boolean isActiveAt(int localTick) {
            return localTick >= startTick && localTick < endTick;
        }

        public enum AnchorMode {
            WORLD,
            FALLBACK
        }
    }

    public record Segment(int segmentIndex, PonderScene scene, int startTick, int durationTicks,
                          List<OverlayCue> cues, boolean extractRuntimeOverlays) {
        public Segment(int segmentIndex, PonderScene scene, int startTick, int durationTicks, List<OverlayCue> cues) {
            this(segmentIndex, scene, startTick, durationTicks, cues, false);
        }

        public boolean containsGlobalTick(int globalTick) {
            return globalTick >= startTick && globalTick < startTick + durationTicks;
        }

        public int localTick(int globalTick) {
            return Math.max(0, globalTick - startTick);
        }
    }

    private final String sceneKey;
    private final List<Segment> segments;
    private final int totalDurationTicks;
    private final BoundingBox combinedBounds;

    private ProjectorSceneBundle(String sceneKey, List<Segment> segments, int totalDurationTicks, BoundingBox combinedBounds) {
        this.sceneKey = sceneKey;
        this.segments = List.copyOf(segments);
        this.totalDurationTicks = totalDurationTicks;
        this.combinedBounds = combinedBounds;
    }

    @Nullable
    public static ProjectorSceneBundle compile(String sceneKey) {
        return compile(List.of(sceneKey));
    }

    @Nullable
    public static ProjectorSceneBundle compile(List<String> sceneKeys) {
        return ProjectorRenderContext.supply(() -> compileRenderOnly(sceneKeys));
    }

    @Nullable
    private static ProjectorSceneBundle compileRenderOnly(List<String> sceneKeys) {
        Set<String> normalizedKeys = new LinkedHashSet<>();
        for (String sceneKey : sceneKeys) {
            if (sceneKey != null && !sceneKey.isBlank()) {
                normalizedKeys.add(sceneKey.trim());
            }
        }
        if (normalizedKeys.isEmpty()) {
            return null;
        }

        List<Segment> segments = new ArrayList<>();
        BoundingBox combinedBounds = null;
        int timeline = 0;
        for (String sceneKey : normalizedKeys) {
            ProjectorSceneBundle compiled;
            try {
                compiled = compileRenderOnly(sceneKey);
            } catch (Throwable ignored) {
                continue;
            }
            if (compiled == null || compiled.segments().isEmpty()) {
                continue;
            }

            for (Segment segment : compiled.segments()) {
                segments.add(new Segment(
                    segment.segmentIndex(),
                    segment.scene(),
                    timeline + segment.startTick(),
                    segment.durationTicks(),
                    segment.cues(),
                    segment.extractRuntimeOverlays()));
            }
            timeline += compiled.totalDurationTicks();
            combinedBounds = combinedBounds == null
                ? compiled.combinedBounds()
                : union(combinedBounds, compiled.combinedBounds());
        }

        if (segments.isEmpty() || combinedBounds == null) {
            return null;
        }

        return new ProjectorSceneBundle(String.join("|", normalizedKeys), segments, timeline, combinedBounds);
    }

    @Nullable
    private static ProjectorSceneBundle compileRenderOnly(String sceneKey) {
        ProjectorSceneKey.Native nativeKey = ProjectorSceneKey.parseNative(sceneKey);
        if (nativeKey != null) {
            return compileNativeRenderOnly(nativeKey);
        }

        DslScene dsl = SceneRuntime.findByKey(sceneKey);
        if (dsl == null || dsl.items == null || dsl.items.isEmpty()) {
            return null;
        }

        ResourceLocation componentId = ResourceLocation.tryParse(dsl.items.get(0));
        if (componentId == null || !PonderIndex.getSceneAccess().doScenesExistForId(componentId)) {
            return null;
        }

        List<PonderScene> compiledScenes = PonderIndex.getSceneAccess().compile(componentId);
        if (compiledScenes.isEmpty()) {
            return null;
        }

        Map<String, Integer> occurrenceById = new HashMap<>();
        List<CompiledSegment> selected = new ArrayList<>();
        for (PonderScene compiledScene : compiledScenes) {
            String ponderSceneId = compiledScene.getId().toString();
            int occurrence = occurrenceById.getOrDefault(ponderSceneId, 0);
            occurrenceById.put(ponderSceneId, occurrence + 1);

            SceneRuntime.SceneMatch match = SceneRuntime.findBySceneId(compiledScene.getId(), occurrence);
            if (match == null || match.scene() == null || !sceneKey.equals(match.scene().sceneKey())) {
                continue;
            }

            int segmentIndex = Math.max(0, match.sceneIndex());
            DslScene.SceneSegment definition = resolveSegmentDefinition(dsl, segmentIndex);
            selected.add(new CompiledSegment(segmentIndex, compiledScene, definition));
        }

        if (selected.isEmpty()) {
            return null;
        }

        selected.sort(Comparator.comparingInt(CompiledSegment::segmentIndex));

        List<Segment> segments = new ArrayList<>(selected.size());
        BoundingBox combinedBounds = selected.get(0).scene().getBounds();
        int timeline = 0;
        for (CompiledSegment selectedSegment : selected) {
            int duration = selectedSegment.scene().getTotalTime();
            if (duration <= 0) {
                duration = Math.max(1, ProjectorSceneCompiler.estimateSegmentTicks(selectedSegment.definition()));
            }
            segments.add(new Segment(
                selectedSegment.segmentIndex(),
                selectedSegment.scene(),
                timeline,
                duration,
                buildOverlayCues(selectedSegment.definition())));
            timeline += duration;
            combinedBounds = union(combinedBounds, selectedSegment.scene().getBounds());
        }

        return new ProjectorSceneBundle(sceneKey, segments, timeline, combinedBounds);
    }

    @Nullable
    private static ProjectorSceneBundle compileNativeRenderOnly(ProjectorSceneKey.Native nativeKey) {
        if (!PonderIndex.getSceneAccess().doScenesExistForId(nativeKey.componentId())) {
            return null;
        }

        List<PonderScene> compiledScenes = PonderIndex.getSceneAccess().compile(nativeKey.componentId());
        if (compiledScenes.isEmpty()) {
            return null;
        }

        Map<ResourceLocation, Integer> occurrenceById = new HashMap<>();
        PonderScene selected = null;
        for (PonderScene compiledScene : compiledScenes) {
            if (compiledScene == null || compiledScene.getId() == null) {
                continue;
            }
            ResourceLocation sceneId = compiledScene.getId();
            int occurrence = occurrenceById.getOrDefault(sceneId, 0);
            occurrenceById.put(sceneId, occurrence + 1);
            if (sceneId.equals(nativeKey.sceneId()) && occurrence == nativeKey.occurrence()) {
                selected = compiledScene;
                break;
            }
        }

        if (selected == null) {
            return null;
        }

        int duration = selected.getTotalTime();
        if (duration <= 0) {
            duration = 20 * 60;
        }

        return new ProjectorSceneBundle(
            ProjectorSceneKey.nativeKey(nativeKey.componentId(), nativeKey.sceneId(), nativeKey.occurrence()),
            List.of(new Segment(0, selected, 0, duration, List.of(), true)),
            duration,
            selected.getBounds());
    }

    public String sceneKey() {
        return sceneKey;
    }

    public List<Segment> segments() {
        return segments;
    }

    public int totalDurationTicks() {
        return totalDurationTicks;
    }

    public BoundingBox combinedBounds() {
        return combinedBounds;
    }

    @Nullable
    public Segment segmentAt(int globalTick) {
        if (segments.isEmpty()) {
            return null;
        }
        for (Segment segment : segments) {
            if (segment.containsGlobalTick(globalTick)) {
                return segment;
            }
        }
        return segments.get(segments.size() - 1);
    }

    public List<OverlayCue> activeCues(int globalTick, float partialTick) {
        return activeCues(globalTick, partialTick, true);
    }

    public List<OverlayCue> activeCues(int globalTick, float partialTick, boolean compatibilityMode) {
        Segment segment = segmentAt(globalTick);
        if (segment == null) {
            return List.of();
        }

        return activeCues(segment, segment.localTick(globalTick), partialTick, compatibilityMode);
    }

    public List<OverlayCue> activeCues(Segment segment, int localTick, float partialTick) {
        return activeCues(segment, localTick, partialTick, true);
    }

    public List<OverlayCue> activeCues(Segment segment, int localTick, float partialTick, boolean compatibilityMode) {
        if (segment == null) {
            return List.of();
        }

        List<OverlayCue> result = new ArrayList<>();
        if (segment.extractRuntimeOverlays()) {
            // Keep runtime cue fallback in lockstep with native overlay fade interpolation.
            result.addAll(ProjectorOverlayExtractor.extract(segment.scene(), localTick, partialTick,
                compatibilityMode));
        }

        List<OverlayCue> cues = ProjectorCueIndexStore.get(segment.scene());
        if (cues.isEmpty()) {
            cues = segment.cues();
        }
        if (cues.isEmpty()) {
            return result.isEmpty() ? List.of() : List.copyOf(result);
        }

        for (OverlayCue cue : cues) {
            if (cue.isActiveAt(localTick)) {
                result.add(cue);
            }
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    private static DslScene.SceneSegment resolveSegmentDefinition(DslScene dsl, int segmentIndex) {
        if (dsl.scenes == null || dsl.scenes.isEmpty()) {
            return new DslScene.SceneSegment();
        }
        return dsl.scenes.get(Math.max(0, Math.min(segmentIndex, dsl.scenes.size() - 1)));
    }

    private static BoundingBox union(BoundingBox first, BoundingBox second) {
        return new BoundingBox(
            Math.min(first.minX(), second.minX()),
            Math.min(first.minY(), second.minY()),
            Math.min(first.minZ(), second.minZ()),
            Math.max(first.maxX(), second.maxX()),
            Math.max(first.maxY(), second.maxY()),
            Math.max(first.maxZ(), second.maxZ()));
    }

    private static List<OverlayCue> buildOverlayCues(@Nullable DslScene.SceneSegment segment) {
        if (segment == null || segment.steps == null || segment.steps.isEmpty()) {
            return List.of();
        }

        List<OverlayCue> cues = new ArrayList<>();
        int timeline = 0;
        for (DslScene.DslStep step : segment.steps) {
            if (step == null || step.type == null) {
                continue;
            }

            String type = step.type.toLowerCase(Locale.ROOT);
            int duration = switch (type) {
                case "text", "shared_text", "show_controls", "show_interface" -> step.durationOrDefault(60);
                default -> ProjectorSceneCompiler.estimateStepTicks(step);
            };

            OverlayCue cue = createOverlayCue(timeline, step);
            if (cue != null) {
                cues.add(cue);
            }

            timeline += Math.max(0, duration);
        }

        return List.copyOf(cues);
    }

    @Nullable
    public static OverlayCue createOverlayCue(int startTick, @Nullable DslScene.DslStep step) {
        if (step == null || step.type == null) {
            return null;
        }

        String type = step.type.toLowerCase(Locale.ROOT);
        int duration = "show_interface".equals(type) ? step.durationOrDefault(60) : 0;
        if (duration <= 0) {
            return null;
        }

        return switch (type) {
            case "show_interface" -> new OverlayCue(
                startTick,
                startTick + Math.max(1, duration),
                resolveCuePoint(step),
                buildInterfaceLines(step),
                0xFFD89B);
            default -> null;
        };
    }

    private static List<Component> buildInterfaceLines(DslScene.DslStep step) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("ponderer.ui.projector.overlay.interface.title"));
        lines.add(Component.translatable("ponderer.ui.projector.overlay.interface.render_only"));
        if (step.uiId != null && !step.uiId.isBlank()) {
            lines.add(Component.literal(step.uiId));
        }
        return List.copyOf(lines);
    }

    private static Vec3 resolveCuePoint(DslScene.DslStep step) {
        if (step.blockPos != null && step.blockPos.size() >= 3) {
            BlockPos first = new BlockPos(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
            BlockPos second = first;
            if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
                second = new BlockPos(step.blockPos2.get(0), step.blockPos2.get(1), step.blockPos2.get(2));
            }
            double centerX = (Math.min(first.getX(), second.getX()) + Math.max(first.getX(), second.getX()) + 1) * 0.5D;
            double centerY = (Math.min(first.getY(), second.getY()) + Math.max(first.getY(), second.getY()) + 1) * 0.5D;
            double centerZ = (Math.min(first.getZ(), second.getZ()) + Math.max(first.getZ(), second.getZ()) + 1) * 0.5D;
            return new Vec3(centerX, centerY, centerZ);
        }

        if (step.point != null && step.point.size() >= 3) {
            return new Vec3(step.point.get(0), step.point.get(1), step.point.get(2));
        }

        return DEFAULT_POINT;
    }

    private record CompiledSegment(int segmentIndex, PonderScene scene, DslScene.SceneSegment definition) {
    }
}
