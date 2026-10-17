package com.nododiiiii.ponderer.ai;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.LocalizedText;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Non-blocking authoring-quality checks used by review_scene and submit_scene.
 */
public final class AiSceneQualityLinter {

    public record Warning(String path, String code, String message) {
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
                if (i + 1 < steps.size()) {
                    DslScene.DslStep next = steps.get(i + 1);
                    if (next != null && "idle".equals(lower(next.type))) {
                        int textDuration = step.durationOrDefault(40);
                        int idleDuration = next.durationOrDefault(20);
                        if (idleDuration < textDuration) {
                            warnings.add(new Warning(path + "[" + (i + 1) + "].duration",
                                "TEXT_OVERLAP_RISK",
                                "Idle duration is shorter than the preceding text duration"));
                        }
                    }
                }
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
