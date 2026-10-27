package com.nododiiiii.ponderer.ai;

import com.google.gson.*;
import java.util.ArrayList;
import java.util.List;
import com.nododiiiii.ponderer.scene.step.SceneStepCatalog;

/** AI diagnostics adapt the shared step contract to the authoring validator. */
final class SceneStepValidation {
    private SceneStepValidation() {}

    static List<AiSceneValidator.Issue> validateFields(JsonObject step, String path) {
        List<AiSceneValidator.Issue> errors = new ArrayList<>();
        if (!step.has("type") || !step.get("type").isJsonPrimitive() || !step.getAsJsonPrimitive("type").isString()) return errors;
        SceneStepCatalog.StepSchema contract = SceneStepCatalog.get(step.get("type").getAsString());
        if (contract == null) return errors;
        for (JsonElement name : contract.inputSchema().getAsJsonArray("required")) {
            String key = name.getAsString();
            if (!step.has(key) || step.get(key).isJsonNull()) errors.add(new AiSceneValidator.Issue(path + "." + key, "STEP_CONTRACT: required field missing"));
        }
        for (var entry : contract.inputSchema().getAsJsonObject("properties").entrySet()) {
            JsonElement value = step.get(entry.getKey());
            if (value == null || value.isJsonNull()) continue;
            try { AiJsonSchema.validate(entry.getValue().getAsJsonObject(), value, path + "." + entry.getKey()); }
            catch (IllegalArgumentException error) { errors.add(new AiSceneValidator.Issue(path + "." + entry.getKey(), "STEP_CONTRACT: " + error.getMessage())); }
        }
        return errors;
    }

    static List<AiSceneValidator.Issue> validateSceneFields(JsonObject scene) {
        List<AiSceneValidator.Issue> errors = new ArrayList<>();
        if (scene.get("scenes") instanceof JsonArray segments) for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i) instanceof JsonObject segment && segment.get("steps") instanceof JsonArray steps) {
                for (int j = 0; j < steps.size(); j++) if (steps.get(j) instanceof JsonObject step)
                    errors.addAll(validateFields(step, "$.scenes[" + i + "].steps[" + j + "]"));
            }
        }
        return errors;
    }
}
