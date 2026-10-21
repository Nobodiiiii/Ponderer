package com.nododiiiii.ponderer.ai;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
                String path = "$.items[" + i + "]";
                requireResourceId(issues, path, scene.items.get(i), "item id");
                validateRegisteredItem(issues, path, scene.items.get(i));
            }
        }
        List<String> declaredStructures = validateDeclaredStructures(
            issues, scene.structures, structureNames);
        if (scene.scenes == null || scene.scenes.isEmpty()) {
            issues.add(new Issue("$.scenes", "at least one scene segment is required"));
            return issues;
        }

        Set<String> segmentIds = new HashSet<>();
        for (int i = 0; i < scene.scenes.size(); i++) {
            DslScene.SceneSegment segment = scene.scenes.get(i);
            if (segment == null || segment.id == null || segment.id.isBlank()) {
                issues.add(new Issue("$.scenes[" + i + "].id", "segment id is required"));
            } else if (!segmentIds.add(segment.id)) {
                issues.add(new Issue("$.scenes[" + i + "].id", "segment id must be unique"));
            }
            validateSegment(issues, "$.scenes[" + i + "]", scene.scenes.get(i),
                structures, structureNames, declaredStructures);
        }
        return issues;
    }

    private static void validateSegment(List<Issue> issues, String path, DslScene.SceneSegment segment,
                                        List<StructureDescriber.StructureInfo> structures,
                                        List<String> structureNames,
                                        List<String> declaredStructures) {
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
            String stepType = step == null ? "" : lower(step.type);
            if ("show_structure".equals(stepType) || "show_extra_structure".equals(stepType)) {
                if (!isKnownStructureReference(
                    step.structure, declaredStructures.size(), declaredStructures)) {
                    issues.add(new Issue(path + ".steps[" + i + "].structure",
                        "structure is not declared in the scene's structures list"));
                } else if (!structureNames.isEmpty()
                    && !isKnownStructureReference(step.structure, structures.size(), structureNames)) {
                    issues.add(new Issue(path + ".steps[" + i + "].structure",
                        "structure is not available in this generation request"));
                }
                if ("show_structure".equals(stepType)) {
                    currentBounds = resolveStructureBounds(
                        step.structure, structures, structureNames, currentBounds);
                }
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

        if (SceneStepCatalog.get(type) == null) {
            issues.add(new Issue(path + ".type", "UNKNOWN_STEP: unsupported step type; query get_step_schema"));
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
                validateIngredient(issues, path + ".item", step.item);
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
                validateBlock(issues, path, step.block, step.blockProperties);
                requireBlockPos(issues, path + ".blockPos", step.blockPos, bounds);
            }
            case "replace_blocks" -> {
                requireResourceId(issues, path + ".block", step.block, "block id");
                validateBlock(issues, path, step.block, step.blockProperties);
                requireBlockPos(issues, path + ".blockPos", step.blockPos, bounds);
                requireBlockPos(issues, path + ".blockPos2", step.blockPos2, bounds);
            }
            case "destroy_block", "indicate_success", "indicate_redstone", "toggle_redstone_power" ->
                requireBlockPos(issues, path + ".blockPos", step.blockPos, bounds);
            case "spawn_particles" -> {
                try {
                    com.nododiiiii.ponderer.ponder.ParticleEffectSpec.validate(step);
                } catch (IllegalArgumentException e) {
                    issues.add(new Issue(path, e.getMessage()));
                }
                requirePoint(issues, path + ".pos", step.pos, bounds, false);
                if (step.particle != null) {
                    String id = step.particle.split("\\{", 2)[0].trim();
                    validateRegisteredId(issues, path + ".particle", id, "particle",
                        location -> BuiltInRegistries.PARTICLE_TYPE.getOptional(location).isPresent());
                }
            }
            case "create_item_entity" -> {
                requireResourceId(issues, path + ".item", step.item, "item id");
                validateRegisteredItem(issues, path + ".item", step.item);
                requirePoint(issues, path + ".pos", step.pos != null ? step.pos : step.point, bounds, false);
            }
            case "create_entity" -> {
                requireResourceId(issues, path + ".entity", step.entity, "entity id");
                validateRegisteredId(issues, path + ".entity", step.entity, "entity",
                    id -> BuiltInRegistries.ENTITY_TYPE.getOptional(id).isPresent());
                requirePoint(issues, path + ".pos", step.pos != null ? step.pos : step.point, bounds, false);
            }
            case "move_section" -> {
                requireString(issues, path + ".linkId", step.linkId, "linkId is required");
                requireDoubleVector(issues, path + ".offset", step.offset, "offset");
            }
            case "rotate_section" -> requireString(issues, path + ".linkId", step.linkId, "linkId is required");
            case "play_sound" -> {
                requireString(issues, path + ".sound", step.sound, "sound id is required");
                validateRegisteredId(issues, path + ".sound", step.sound, "sound",
                    id -> BuiltInRegistries.SOUND_EVENT.getOptional(id).isPresent());
            }
            default -> {
                // Known catalog entries may use their runtime-specific validators.
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

    private static void validateBlock(List<Issue> issues, String path, @Nullable String blockId,
                                      @Nullable Map<String, String> properties) {
        ResourceLocation id = blockId == null ? null : ResourceLocation.tryParse(blockId);
        if (id == null) {
            return;
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null) {
            issues.add(new Issue(path + ".block", "block id is not registered"));
            return;
        }
        if (properties == null || properties.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            Property<?> property = block.getStateDefinition().getProperty(entry.getKey());
            if (property == null) {
                issues.add(new Issue(path + ".blockProperties." + entry.getKey(),
                    "property is not valid for " + blockId));
            } else if (property.getValue(entry.getValue()).isEmpty()) {
                issues.add(new Issue(path + ".blockProperties." + entry.getKey(),
                    "value '" + entry.getValue() + "' is not valid for " + blockId));
            }
        }
    }

    private static void validateRegisteredId(List<Issue> issues, String path, @Nullable String value,
                                             String label,
                                             java.util.function.Predicate<ResourceLocation> exists) {
        ResourceLocation id = value == null ? null : ResourceLocation.tryParse(value);
        if (id != null && !exists.test(id)) {
            issues.add(new Issue(path, label + " id is not registered"));
        }
    }

    private static List<String> validateDeclaredStructures(
        List<Issue> issues, @Nullable List<String> declared, List<String> available) {
        if (declared == null || declared.isEmpty()) {
            issues.add(new Issue("$.structures", "at least one structure must be declared"));
            return List.of();
        }
        List<String> valid = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < declared.size(); i++) {
            String value = declared.get(i);
            String path = "$.structures[" + i + "]";
            if (value == null || value.isBlank() || ResourceLocation.tryParse(value) == null) {
                issues.add(new Issue(path, "structure id must be a valid resource location"));
                continue;
            }
            String normalized = value.toLowerCase(Locale.ROOT);
            if (!seen.add(normalized)) {
                issues.add(new Issue(path, "structure id must be unique"));
            }
            if (available != null && !available.isEmpty()
                && available.stream().noneMatch(value::equalsIgnoreCase)) {
                issues.add(new Issue(path, "structure is not available in this generation request"));
            }
            valid.add(value);
        }
        return List.copyOf(valid);
    }

    private static void validateRegisteredItem(
        List<Issue> issues, String path, @Nullable String value) {
        ResourceLocation id = value == null ? null : ResourceLocation.tryParse(value);
        if (id != null && BuiltInRegistries.ITEM.getOptional(id).isEmpty()) {
            issues.add(new Issue(path, "item id is not registered"));
        }
    }

    private static void validateIngredient(
        List<Issue> issues, String path, @Nullable String value) {
        if (ingredientStatus(value) == IngredientStatus.MISSING) {
            issues.add(new Issue(path, "ingredient id is not registered"));
        }
    }

    static IngredientStatus ingredientStatus(@Nullable String value) {
        ResourceLocation id = value == null ? null : ResourceLocation.tryParse(value);
        if (id == null) {
            return IngredientStatus.MISSING;
        }
        if (BuiltInRegistries.ITEM.getOptional(id).isPresent()
            || BuiltInRegistries.FLUID.getOptional(id).isPresent()) {
            return IngredientStatus.PRESENT;
        }
        try {
            if (!JeiCompat.isAvailable()) {
                return IngredientStatus.UNKNOWN_WITHOUT_JEI;
            }
            boolean present = JeiCompat.getAllExtraIngredientEntries().stream()
                .anyMatch(entry -> entry.length > 0 && value.equalsIgnoreCase(entry[0]));
            return present ? IngredientStatus.PRESENT : IngredientStatus.MISSING;
        } catch (Throwable ignored) {
            return IngredientStatus.UNKNOWN_WITHOUT_JEI;
        }
    }

    enum IngredientStatus {
        PRESENT,
        MISSING,
        UNKNOWN_WITHOUT_JEI
    }

    private static boolean isKnownStructureReference(@Nullable String requested, int structureCount,
                                                     List<String> structureNames) {
        if (requested == null || requested.isBlank()) {
            return structureCount == 1;
        }
        if (parseStructureIndex(requested.trim(), structureCount) != null) {
            return true;
        }
        String normalized = requested.contains(":") ? requested : "ponderer:" + requested;
        return structureNames.stream().anyMatch(name ->
            requested.equalsIgnoreCase(name) || normalized.equalsIgnoreCase(name));
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
