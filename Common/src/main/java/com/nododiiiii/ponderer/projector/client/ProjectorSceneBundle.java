package com.nododiiiii.ponderer.projector.client;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

    public record OverlayCue(int startTick, int endTick, Vec3 point, List<Component> lines, int accentColor) {
        public boolean isActiveAt(int localTick) {
            return localTick >= startTick && localTick < endTick;
        }
    }

    public record Segment(int segmentIndex, PonderScene scene, int startTick, int durationTicks, List<OverlayCue> cues) {
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
            ProjectorSceneBundle compiled = compileRenderOnly(sceneKey);
            if (compiled == null || compiled.segments().isEmpty()) {
                continue;
            }

            for (Segment segment : compiled.segments()) {
                segments.add(new Segment(
                    segment.segmentIndex(),
                    segment.scene(),
                    timeline + segment.startTick(),
                    segment.durationTicks(),
                    segment.cues()));
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

    public List<OverlayCue> activeCues(int globalTick) {
        Segment segment = segmentAt(globalTick);
        if (segment == null) {
            return List.of();
        }

        int localTick = segment.localTick(globalTick);
        List<OverlayCue> cues = ProjectorCueIndexStore.get(segment.scene());
        if (cues.isEmpty()) {
            cues = segment.cues();
        }
        if (cues.isEmpty()) {
            return List.of();
        }

        List<OverlayCue> result = new ArrayList<>();
        for (OverlayCue cue : cues) {
            if (cue.isActiveAt(localTick)) {
                result.add(cue);
            }
        }
        return result;
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
        int duration = switch (type) {
            case "text", "shared_text", "show_controls", "show_interface" -> step.durationOrDefault(60);
            default -> 0;
        };
        if (duration <= 0) {
            return null;
        }

        return switch (type) {
            case "text" -> {
                if (step.text == null || step.text.isEmpty()) {
                    yield null;
                }
                yield new OverlayCue(
                    startTick,
                    startTick + Math.max(1, duration),
                    resolveCuePoint(step),
                    List.of(Component.literal(step.text.resolve())),
                    0xE6FCFF);
            }
            case "shared_text" -> new OverlayCue(
                startTick,
                startTick + Math.max(1, duration),
                resolveCuePoint(step),
                List.of(resolveSharedText(step.key)),
                0xD9F4FF);
            case "show_controls" -> new OverlayCue(
                startTick,
                startTick + Math.max(1, duration),
                resolveCuePoint(step),
                buildControlLines(step),
                0x9CEBFF);
            case "show_interface" -> new OverlayCue(
                startTick,
                startTick + Math.max(1, duration),
                resolveCuePoint(step),
                buildInterfaceLines(step),
                0xFFD89B);
            default -> null;
        };
    }

    private static Component resolveSharedText(@Nullable String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Component.translatable("ponderer.ui.projector.overlay.missing_shared_text");
        }

        ResourceLocation loc = rawKey.contains(":")
            ? ResourceLocation.tryParse(rawKey)
            : new ResourceLocation("ponderer", rawKey);
        if (loc == null) {
            return Component.literal(rawKey);
        }

        String translationKey = loc.getNamespace() + "." + PonderLocalization.LANG_PREFIX + "shared." + loc.getPath();
        String translated = I18n.get(translationKey);
        if (translated == null || translated.equals(translationKey)) {
            return Component.literal(rawKey);
        }
        return Component.literal(translated);
    }

    private static List<Component> buildControlLines(DslScene.DslStep step) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(controlActionKey(step.action)));

        String ingredientName = resolveIngredientName(step.item);
        if (!ingredientName.isBlank()) {
            lines.add(Component.translatable("ponderer.ui.projector.overlay.control.with_item", ingredientName));
        }

        List<Component> modifiers = new ArrayList<>();
        if (Boolean.TRUE.equals(step.whileSneaking)) {
            modifiers.add(Component.translatable("ponderer.ui.projector.overlay.modifier.sneak"));
        }
        if (Boolean.TRUE.equals(step.whileCTRL)) {
            modifiers.add(Component.translatable("ponderer.ui.projector.overlay.modifier.ctrl"));
        }
        if (!modifiers.isEmpty()) {
            if (modifiers.size() == 1) {
                lines.add(Component.translatable("ponderer.ui.projector.overlay.control.hold", modifiers.get(0)));
            } else {
                lines.add(Component.translatable("ponderer.ui.projector.overlay.control.hold_pair",
                    modifiers.get(0), modifiers.get(1)));
            }
        }

        return List.copyOf(lines);
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

    private static String controlActionKey(@Nullable String action) {
        if (action == null || action.isBlank()) {
            return "ponderer.ui.projector.overlay.control.action";
        }
        return switch (action.toLowerCase(Locale.ROOT)) {
            case "left" -> "ponderer.ui.projector.overlay.control.left_click";
            case "right" -> "ponderer.ui.projector.overlay.control.right_click";
            case "scroll" -> "ponderer.ui.projector.overlay.control.scroll";
            default -> "ponderer.ui.projector.overlay.control.action";
        };
    }

    private static String resolveIngredientName(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String itemId = raw;
        int nbtStart = raw.indexOf('{');
        if (nbtStart >= 0) {
            itemId = raw.substring(0, nbtStart).trim();
        }

        ResourceLocation loc = ResourceLocation.tryParse(itemId);
        if (loc == null) {
            return raw;
        }

        Item item = BuiltInRegistries.ITEM.getOptional(loc).orElse(null);
        if (item == null) {
            return loc.toString();
        }
        return new ItemStack(item).getHoverName().getString();
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
