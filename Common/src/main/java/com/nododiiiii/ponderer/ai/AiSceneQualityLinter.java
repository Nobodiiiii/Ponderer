package com.nododiiiii.ponderer.ai;

import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.scene.LocalizedText;
import com.nododiiiii.ponderer.scene.interfaces.InterfaceStepSpecs;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Non-blocking authoring-quality checks used by review_scene and submit_scene.
 */
public final class AiSceneQualityLinter {

    public record Warning(String path, String code, String message) {
        public String priority() {
            return switch (code) {
                case "TEXT_OVERLAP_RISK", "TEXT_MISSING", "ITEM_ENTITY_LEAK" -> "high";
                default -> "normal";
            };
        }

        public String suggestion() {
            return switch (code) {
                case "TEXT_OVERLAP_RISK" -> "Accumulate enough idle or known blocking action time between annotations; non-blocking durations are not waits. Retain overlap only when deliberate or explained by runtime animation intervals.";
                case "TEXT_WALL", "NAKED_TEXT", "LOW_VISUAL_DENSITY" -> "Pair explanations with relevant visual demonstrations within the authorized scope.";
                case "TEXT_MISSING", "LOCALIZATION_INCOMPLETE" -> "Provide readable en_us and zh_cn text within the authorized scope.";
                case "ITEM_ENTITY_NOT_CLEARED", "ITEM_ENTITY_LEAK" -> "Clear demonstrated item entities after allowing observation time.";
                case "ITEM_MOTION_MISSING" -> "Provide an explicit item motion vector, including [0,0,0] for a stationary item.";
                case "INGREDIENT_UNVERIFIED" -> "Verify the ingredient through available runtime evidence; do not invent an ID.";
                case "INTERACTION_ACTION_MISSING" -> "Set the intended player click action; omit it only for machine input/output indicators.";
                default -> "Review the warning and repair it when relevant and within scope.";
            };
        }
    }

    private AiSceneQualityLinter() {
    }

    public static List<Warning> lint(@Nullable DslScene scene) {
        if (scene == null || scene.scenes == null) {
            return List.of();
        }
        List<Warning> warnings = new ArrayList<>();
        checkLocalized(warnings, "$.title", scene.title);
        for (int segmentIndex = 0; segmentIndex < scene.scenes.size(); segmentIndex++) {
            DslScene.SceneSegment segment = scene.scenes.get(segmentIndex);
            if (segment == null) {
                continue;
            }
            String path = "$.scenes[" + segmentIndex + "]";
            checkLocalized(warnings, path + ".title", segment.title);
            lintSteps(warnings, path + ".steps", segment.steps);
        }
        return List.copyOf(warnings);
    }

    private static void lintSteps(List<Warning> warnings, String path, @Nullable List<DslScene.DslStep> steps) {
        if (steps == null) {
            return;
        }
        int consecutiveTexts = 0;
        boolean visualSinceLastText = false;
        boolean outstandingItemEntity = false;
        int visualActions = 0;
        lintTextTiming(warnings, path, steps);

        for (int i = 0; i < steps.size(); i++) {
            DslScene.DslStep step = steps.get(i);
            if (step == null) {
                continue;
            }
            String type = step.type == null ? "" : step.type.trim().toLowerCase(Locale.ROOT);
            String stepPath = path + "[" + i + "]";

            if ("idle".equals(type)) {
                continue;
            }
            if ("text".equals(type)) {
                consecutiveTexts++;
                if (consecutiveTexts >= 3) {
                    warnings.add(new Warning(stepPath, "TEXT_WALL",
                        "Three or more text steps appear without an intervening visual action"));
                }
                if (!visualSinceLastText) {
                    warnings.add(new Warning(stepPath, "NAKED_TEXT",
                        "Text is not paired with a preceding visual action"));
                }
                checkLocalized(warnings, stepPath + ".text", step.text);
                visualSinceLastText = false;
                continue;
            }
            if ("shared_text".equals(type)) {
                consecutiveTexts++;
                visualSinceLastText = false;
                continue;
            }

            consecutiveTexts = 0;
            if (!"next_scene".equals(type)) {
                visualSinceLastText = true;
                visualActions++;
            }

            if ("create_item_entity".equals(type)) {
                if (outstandingItemEntity) {
                    warnings.add(new Warning(stepPath, "ITEM_ENTITY_NOT_CLEARED",
                        "Another item entity is created before the previous demonstration is cleared"));
                }
                if (step.motion == null || step.motion.size() != 3) {
                    warnings.add(new Warning(stepPath + ".motion", "ITEM_MOTION_MISSING",
                        "create_item_entity should provide an explicit motion vector"));
                }
                outstandingItemEntity = true;
            } else if ("clear_item_entities".equals(type)) {
                outstandingItemEntity = false;
            } else if ("show_controls".equals(type)) {
                if (AiSceneValidator.ingredientStatus(step.item)
                    == AiSceneValidator.IngredientStatus.UNKNOWN_WITHOUT_JEI) {
                    warnings.add(new Warning(stepPath + ".item", "INGREDIENT_UNVERIFIED",
                        "JEI is unavailable, so this non-item ingredient ID could not be verified"));
                }
                if ((step.action == null || step.action.isBlank())
                    && looksLikeInteraction(steps, i, step)) {
                    warnings.add(new Warning(stepPath + ".action", "INTERACTION_ACTION_MISSING",
                        "This control appears to describe player interaction but has no action"));
                }
            }
        }

        if (outstandingItemEntity) {
            warnings.add(new Warning(path, "ITEM_ENTITY_LEAK",
                "The segment ends without clearing its demonstrated item entities"));
        }
        if (visualActions <= 1 && !steps.isEmpty()) {
            warnings.add(new Warning(path, "LOW_VISUAL_DENSITY",
                "The segment contains almost no visual actions beyond its initial structure"));
        }
    }

    private static void lintTextTiming(List<Warning> warnings, String path, List<DslScene.DslStep> steps) {
        int precedingDuration = 0;
        long elapsed = 0;
        for (int i = 0; i < steps.size(); i++) {
            DslScene.DslStep step = steps.get(i);
            if (step == null) continue;
            String type = lower(step.type);
            if ("text".equals(type) || "shared_text".equals(type)) {
                if (elapsed < precedingDuration) {
                    warnings.add(new Warning(path + "[" + i + "]", "TEXT_OVERLAP_RISK",
                        "Only " + elapsed + " known blocking ticks separate this annotation from the preceding text,"
                            + " which displays for " + precedingDuration + " ticks; shortfall "
                            + (precedingDuration - elapsed) + " ticks. Add cumulative idle before this text or"
                            + " confirm intentional overlap/runtime-dependent animation intervals. Text and effect"
                            + " durations do not themselves advance the timeline."));
                }
                precedingDuration = Math.max(0, step.durationOrDefault(60));
                elapsed = 0;
            } else {
                elapsed += blockingTicks(step, type);
            }
        }
    }

    /** Conservative pacing check, not a playback simulation. Entrance intervals depend on runtime visibility. */
    private static int blockingTicks(DslScene.DslStep step, String type) {
        return switch (type) {
            case "idle", "rotate_camera_y" -> Math.max(0, step.durationOrDefault(20));
            case "zoom_scene" -> step.scale != null && step.scale > 0
                ? Math.max(1, step.durationOrDefault(20)) : 0;
            case "show_interface" -> 1;
            case "interface_action", "click_interface" -> {
                try {
                    InterfaceStepSpecs.Action action = "click_interface".equals(type)
                        ? InterfaceStepSpecs.fromLegacyClick(step) : InterfaceStepSpecs.action(step);
                    yield action == null ? 0 : Math.max(1, action.duration());
                } catch (RuntimeException invalidAction) {
                    // The validator reports malformed actions; quality review must still return its results.
                    yield 0;
                }
            }
            // await_interface may complete immediately or be skipped during projector playback.
            default -> 0;
        };
    }

    private static void checkLocalized(List<Warning> warnings, String path, @Nullable LocalizedText text) {
        if (text == null || text.isEmpty()) {
            warnings.add(new Warning(path, "TEXT_MISSING", "Localized text is missing"));
            return;
        }
        if (text.isPlain() || isBlank(text.getExact("en_us")) || isBlank(text.getExact("zh_cn"))) {
            warnings.add(new Warning(path, "LOCALIZATION_INCOMPLETE",
                "Provide both en_us and zh_cn translations"));
        }
    }

    private static boolean looksLikeInteraction(List<DslScene.DslStep> steps, int index,
                                                DslScene.DslStep control) {
        if (Boolean.TRUE.equals(control.whileSneaking) || Boolean.TRUE.equals(control.whileCTRL)) {
            return true;
        }
        int from = Math.max(0, index - 2);
        int to = Math.min(steps.size() - 1, index + 2);
        for (int i = from; i <= to; i++) {
            DslScene.DslStep nearby = steps.get(i);
            if (nearby == null || nearby.text == null) {
                continue;
            }
            String text = nearby.text.getAllTranslations().values().stream()
                .filter(value -> value != null)
                .reduce("", (left, right) -> left + " " + right)
                .toLowerCase(Locale.ROOT);
            if (text.contains("click") || text.contains("right-click") || text.contains("left-click")
                || text.contains("scroll") || text.contains("sneak") || text.contains("interact")
                || text.contains("点击") || text.contains("右键") || text.contains("左键")
                || text.contains("滚轮") || text.contains("潜行") || text.contains("交互")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    private static String lower(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
