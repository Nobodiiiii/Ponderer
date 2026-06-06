package com.nododiiiii.ponderer.ai;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic checks used by the multi-round agent loop before a scene is saved.
 */
public final class AiSceneValidator {

    public record Issue(String path, String message) {
        public String format() {
            return path + ": " + message;
        }
    }

    private AiSceneValidator() {
    }

    public static List<Issue> validate(@Nullable DslScene scene,
                                       List<StructureDescriber.StructureInfo> structures) {
        return validate(scene, structures, List.of());
    }

    public static List<Issue> validate(@Nullable DslScene scene,
                                       List<StructureDescriber.StructureInfo> structures,
                                       List<String> structureNames) {
        List<Issue> issues = new ArrayList<>();
        if (scene == null) {
            issues.add(new Issue("$", "scene JSON could not be parsed"));
            return issues;
        }
        if (scene.id == null || scene.id.isBlank()) {
            issues.add(new Issue("$.id", "scene id is required"));
        } else if (ResourceLocation.tryParse(scene.id) == null) {
            issues.add(new Issue("$.id", "scene id must be a valid resource location"));
        }
        if (scene.items == null || scene.items.isEmpty()) {
            issues.add(new Issue("$.items", "at least one carrier item is required"));
        } else {
            for (int i = 0; i < scene.items.size(); i++) {
                requireResourceId(issues, "$.items[" + i + "]", scene.items.get(i), "item id");
            }
        }
        if (scene.scenes == null || scene.scenes.isEmpty()) {
            issues.add(new Issue("$.scenes", "at least one scene segment is required"));
            return issues;
        }

        for (int i = 0; i < scene.scenes.size(); i++) {
            validateSegment(issues, "$.scenes[" + i + "]", scene.scenes.get(i), structures, structureNames);
        }
        return issues;
    }

    private static void validateSegment(List<Issue> issues, String path, DslScene.SceneSegment segment,
                                        List<StructureDescriber.StructureInfo> structures,
                                        List<String> structureNames) {
        if (segment == null) {
            issues.add(new Issue(path, "segment must not be null"));
            return;
        }
        if (segment.steps == null || segment.steps.isEmpty()) {
            issues.add(new Issue(path + ".steps", "segment must contain steps"));
            return;
        }
        DslScene.DslStep first = firstMeaningfulStep(segment.steps);
        if (first == null) {
            issues.add(new Issue(path + ".steps", "segment has no meaningful steps"));
        } else {
            String type = lower(first.type);
            if (!"show_structure".equals(type) && !"show_interface".equals(type)) {
                issues.add(new Issue(path + ".steps[0].type", "first meaningful step must be show_structure or show_interface"));
            }
        }

        StructureDescriber.StructureInfo currentBounds = firstStructure(structures);
        for (int i = 0; i < segment.steps.size(); i++) {
            DslScene.DslStep step = segment.steps.get(i);
            if (step != null && "show_structure".equals(lower(step.type))) {
                currentBounds = resolveStructureBounds(step.structure, structures, structureNames, currentBounds);
            }
            validateStep(issues, path + ".steps[" + i + "]", step, currentBounds);
        }
    }

    @Nullable
    private static DslScene.DslStep firstMeaningfulStep(List<DslScene.DslStep> steps) {
        for (DslScene.DslStep step : steps) {
            if (step != null && step.type != null && !step.type.isBlank()) {
                return step;
            }
        }
        return null;
    }

    private static void validateStep(List<Issue> issues, String path, DslScene.DslStep step,
                                     @Nullable StructureDescriber.StructureInfo bounds) {
        if (step == null) {
            issues.add(new Issue(path, "step must not be null"));
            return;
        }
        String type = lower(step.type);
        if (type.isBlank()) {
            issues.add(new Issue(path + ".type", "step type is required"));
            return;
        }

        switch (type) {
            case "idle" -> requireNonNegative(issues, path + ".duration", step.duration, "duration");
            case "text" -> {
                if (step.text == null) {
                    issues.add(new Issue(path + ".text", "text step requires localized text"));
                }
                requirePoint(issues, path + ".point", step.point, bounds, true);
            }
            case "show_controls" -> {
                requirePoint(issues, path + ".point", step.point, bounds, true);
                requireResourceId(issues, path + ".item", step.item, "item id");
            }
            case "show_structure" -> {
                // Whole-structure reveal; no block positions are required.
            }
            case "show_section", "hide_section", "show_section_and_merge" -> {
                requireBlockPos(issues, path + ".blockPos", step.blockPos, bounds);
                requireBlockPos(issues, path + ".blockPos2", step.blockPos2, bounds);
            }
            case "set_block" -> {
                requireResourceId(issues, path + ".block", step.block, "block id");
                requireBlockPos(issues, path + ".blockPos", step.blockPos, bounds);
            }
            case "replace_blocks" -> {
                requireResourceId(issues, path + ".block", step.block, "block id");
                requireBlockPos(issues, path + ".blockPos", step.blockPos, bounds);
                requireBlockPos(issues, path + ".blockPos2", step.blockPos2, bounds);
            }
            case "destroy_block", "indicate_success", "indicate_redstone", "toggle_redstone_power" ->
                requireBlockPos(issues, path + ".blockPos", step.blockPos, bounds);
            case "create_item_entity" -> {
                requireResourceId(issues, path + ".item", step.item, "item id");
                requirePoint(issues, path + ".pos", step.pos != null ? step.pos : step.point, bounds, false);
            }
            case "create_entity" -> {
                requireResourceId(issues, path + ".entity", step.entity, "entity id");
                requirePoint(issues, path + ".pos", step.pos != null ? step.pos : step.point, bounds, false);
            }
            case "move_section" -> {
                requireString(issues, path + ".linkId", step.linkId, "linkId is required");
                requireDoubleVector(issues, path + ".offset", step.offset, "offset");
            }
            case "rotate_section" -> requireString(issues, path + ".linkId", step.linkId, "linkId is required");
            case "play_sound" -> requireString(issues, path + ".sound", step.sound, "sound id is required");
            default -> {
                // Unknown step types are allowed by the runtime more often than this validator knows.
                // The required type check above still catches malformed empty steps.
            }
        }
    }

    private static void requireResourceId(List<Issue> issues, String path, @Nullable String value, String label) {
        if (value == null || value.isBlank()) {
            issues.add(new Issue(path, label + " is required"));
            return;
        }
        if (ResourceLocation.tryParse(value) == null) {
            issues.add(new Issue(path, label + " must be a valid resource location"));
        }
    }

    private static void requireString(List<Issue> issues, String path, @Nullable String value, String message) {
        if (value == null || value.isBlank()) {
            issues.add(new Issue(path, message));
        }
    }

    private static void requireNonNegative(List<Issue> issues, String path, @Nullable Integer value, String label) {
        if (value != null && value < 0) {
            issues.add(new Issue(path, label + " must be non-negative"));
        }
    }

    private static void requireBlockPos(List<Issue> issues, String path, @Nullable List<Integer> value,
                                        @Nullable StructureDescriber.StructureInfo bounds) {
        if (value == null || value.size() != 3) {
            issues.add(new Issue(path, "block position must be [x,y,z]"));
            return;
        }
        if (bounds == null) {
            return;
        }
        int x = value.get(0);
        int y = value.get(1);
        int z = value.get(2);
        if (x < 0 || y < 0 || z < 0 || x >= bounds.sizeX() || y >= bounds.sizeY() || z >= bounds.sizeZ()) {
            issues.add(new Issue(path, "block position is outside the current structure bounds "
                + bounds.sizeX() + "x" + bounds.sizeY() + "x" + bounds.sizeZ()));
        }
    }

    private static void requirePoint(List<Issue> issues, String path, @Nullable List<Double> value,
                                     @Nullable StructureDescriber.StructureInfo bounds, boolean allowOmitted) {
        if (value == null) {
            if (!allowOmitted) {
                issues.add(new Issue(path, "point must be [x,y,z]"));
            }
            return;
        }
        if (value.size() != 3) {
            issues.add(new Issue(path, "point must be [x,y,z]"));
            return;
        }
        if (bounds == null) {
            return;
        }
        double x = value.get(0);
        double y = value.get(1);
        double z = value.get(2);
        if (x < 0.0d || y < 0.0d || z < 0.0d
            || x > bounds.sizeX() || y > bounds.sizeY() || z > bounds.sizeZ()) {
            issues.add(new Issue(path, "point is outside the current structure bounds "
                + bounds.sizeX() + "x" + bounds.sizeY() + "x" + bounds.sizeZ()));
        }
    }

    private static void requireDoubleVector(List<Issue> issues, String path, @Nullable List<Double> value,
                                            String label) {
        if (value == null || value.size() != 3) {
            issues.add(new Issue(path, label + " must be [x,y,z]"));
        }
    }

    @Nullable
    private static StructureDescriber.StructureInfo firstStructure(List<StructureDescriber.StructureInfo> structures) {
        return structures == null || structures.isEmpty() ? null : structures.get(0);
    }

    @Nullable
    private static StructureDescriber.StructureInfo resolveStructureBounds(@Nullable String structure,
                                                                          List<StructureDescriber.StructureInfo> structures,
                                                                          List<String> structureNames,
                                                                          @Nullable StructureDescriber.StructureInfo fallback) {
        if (structures == null || structures.isEmpty()) {
            return null;
        }
        if (structure == null || structure.isBlank()) {
            return fallback != null ? fallback : structures.get(0);
        }

        String requested = structure.trim();
        Integer numericIndex = parseStructureIndex(requested, structures.size());
        if (numericIndex != null) {
            return structures.get(numericIndex);
        }

        String normalized = requested.contains(":") ? requested : "ponderer:" + requested;
        for (int i = 0; i < structureNames.size() && i < structures.size(); i++) {
            String known = structureNames.get(i);
            if (requested.equalsIgnoreCase(known) || normalized.equalsIgnoreCase(known)) {
                return structures.get(i);
            }
        }
        return fallback != null ? fallback : structures.get(0);
    }

    @Nullable
    private static Integer parseStructureIndex(String value, int size) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed >= 1 && parsed <= size) {
                return parsed - 1;
            }
            if (parsed >= 0 && parsed < size) {
                return parsed;
            }
            return null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String lower(@Nullable String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }
}
