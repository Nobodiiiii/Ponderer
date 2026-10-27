package com.nododiiiii.ponderer.ai;

import com.google.gson.*;
import java.util.*;

/** Mechanical edit boundaries. Natural-language intent remains subject to the user's review. */
final class AiEditGuard {
    private static final Set<String> PROTECTED = Set.of("id", "pack", "editable", "items", "nbtFilter", "itemNbtFilters");

    private AiEditGuard() {}

    static List<AiSceneValidator.Issue> validate(AgentContext context, JsonObject draft) {
        if (!context.existingScene().available()) return List.of();
        JsonObject original = context.existingScene().json();
        List<String> changes = changedPaths(original, draft);
        changes.removeIf(path -> PROTECTED.stream().anyMatch(field -> within(path, "/" + field)));
        if (changes.isEmpty()) return List.of();
        JsonObject scope = context.runState().editScope();
        List<AiSceneValidator.Issue> errors = new ArrayList<>();
        if (context.editMode() == AiSceneAgentPipeline.Request.EditMode.REWRITE) return List.of();
        Set<String> paths = new LinkedHashSet<>();
        if (scope != null && scope.has("paths") && scope.get("paths").isJsonArray()) {
            for (JsonElement entry : scope.getAsJsonArray("paths")) {
                if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) continue;
                String path = entry.getAsString();
                // Whole-scene/segment/steps-array grants are deliberately not accepted.
                if (path.matches("/scenes/\\d+/steps/\\d+(/[^/]+)*")
                    || path.matches("/scenes/\\d+/title(/[^/]+)*")
                    || path.matches("/(title|tags|structures|customTags)(/[^/]+)*")) paths.add(path);
            }
        }
        for (String change : changes) {
            if (change.startsWith("/scenes/")) {
                try {
                    int index = Integer.parseInt(change.split("/")[2]);
                    JsonArray segments = original.getAsJsonArray("scenes");
                    if (index >= segments.size() || !context.runState().inspectedSegment(segments.get(index).getAsJsonObject().get("id").getAsString())) {
                        errors.add(new AiSceneValidator.Issue(change, "UNREAD_SEGMENT: inspect the target segment with include_steps=true before editing"));
                    }
                } catch (RuntimeException malformed) {
                    errors.add(new AiSceneValidator.Issue(change, "INVALID_SEGMENT: cannot resolve the original segment"));
                }
            }
            if (paths.stream().noneMatch(path -> within(change, path))) {
                errors.add(new AiSceneValidator.Issue(change,
                    "EDIT_OUT_OF_SCOPE: declare exact JSON pointer paths in edit_scope.paths before editing; broad scene/segment grants are not allowed"));
            }
        }
        String instruction = context.userPrompt().toLowerCase(Locale.ROOT);
        boolean additive = instruction.matches("(?s).*(添加|增加|插入|补充|\\badd\\b|\\bappend\\b|\\binsert\\b).*" )
            && !instruction.matches("(?s).*(修改|调整|更改|替换|删除|重写|\\bchange\\b|\\breplace\\b|\\bremove\\b|\\brewrite\\b).*" );
        if (additive && !preservesOriginal(original, draft, "")) {
            errors.add(new AiSceneValidator.Issue("$agent", "EDIT_NOT_ADDITIVE: this add/insert request must preserve existing steps and metadata"));
        }
        return errors;
    }

    private static boolean preservesOriginal(JsonElement original, JsonElement draft, String path) {
        if (Objects.equals(original, draft)) return true;
        if (original == null || draft == null) return false;
        if (original.isJsonObject() && draft.isJsonObject()) {
            for (var entry : original.getAsJsonObject().entrySet()) {
                if (path.isEmpty() && PROTECTED.contains(entry.getKey())) continue;
                if (!preservesOriginal(entry.getValue(), draft.getAsJsonObject().get(entry.getKey()), path + "/" + entry.getKey())) return false;
            }
            for (String key : draft.getAsJsonObject().keySet()) {
                if (!original.getAsJsonObject().has(key) && !(path.isEmpty() && PROTECTED.contains(key))) return false;
            }
            return true;
        }
        if (original.isJsonArray() && draft.isJsonArray() && path.matches("/scenes/\\d+/steps")) {
            return isSubsequence(original.getAsJsonArray(), draft.getAsJsonArray());
        }
        if (original.isJsonArray() && draft.isJsonArray() && original.getAsJsonArray().size() == draft.getAsJsonArray().size()) {
            for (int i = 0; i < original.getAsJsonArray().size(); i++) {
                if (!preservesOriginal(original.getAsJsonArray().get(i), draft.getAsJsonArray().get(i), path + "/" + i)) return false;
            }
            return true;
        }
        return false;
    }

    static List<String> changedPaths(JsonElement original, JsonElement draft) {
        List<String> result = new ArrayList<>();
        diff(original, draft, "", result);
        return result;
    }

    private static void diff(JsonElement a, JsonElement b, String path, List<String> out) {
        if (Objects.equals(a, b)) return;
        if (a != null && b != null && a.isJsonObject() && b.isJsonObject()) {
            Set<String> keys = new LinkedHashSet<>(a.getAsJsonObject().keySet());
            keys.addAll(b.getAsJsonObject().keySet());
            for (String key : keys) diff(a.getAsJsonObject().get(key), b.getAsJsonObject().get(key),
                path + "/" + key.replace("~", "~0").replace("/", "~1"), out);
        } else if (a != null && b != null && a.isJsonArray() && b.isJsonArray()) {
            JsonArray old = a.getAsJsonArray(), next = b.getAsJsonArray();
            if (path.matches("/scenes/\\d+/steps") && isSubsequence(old, next)) {
                int j = 0;
                for (int i = 0; i < next.size(); i++) {
                    if (j < old.size() && old.get(j).equals(next.get(i))) j++;
                    else out.add(path + "/" + i);
                }
            } else {
                for (int i = 0; i < Math.max(old.size(), next.size()); i++) {
                    diff(i < old.size() ? old.get(i) : null, i < next.size() ? next.get(i) : null, path + "/" + i, out);
                }
            }
        } else out.add(path.isEmpty() ? "/" : path);
    }

    private static boolean isSubsequence(JsonArray a, JsonArray b) {
        int i = 0;
        for (JsonElement value : b) if (i < a.size() && a.get(i).equals(value)) i++;
        return i == a.size();
    }

    private static boolean within(String path, String parent) {
        return path.equals(parent) || path.startsWith(parent + "/");
    }

    static JsonArray warnings(AgentContext context, JsonObject draft, List<AiSceneQualityLinter.Warning> warnings) {
        JsonArray result = new JsonArray();
        List<AiSceneQualityLinter.Warning> baseline = context.existingScene().available()
            ? AiSceneQualityLinter.lint(context.existingScene().scene()) : List.of();
        for (var warning : warnings) {
            JsonObject item = new JsonObject();
            item.addProperty("path", warning.path());
            item.addProperty("code", warning.code());
            item.addProperty("message", warning.message());
            item.addProperty("priority", warning.priority());
            item.addProperty("suggestion", warning.suggestion());
            String source = identity(draft, warning.path());
            boolean existed = baseline.stream().anyMatch(old -> old.code().equals(warning.code())
                && old.message().equals(warning.message())
                && identity(context.existingScene().json(), old.path()).equals(source));
            boolean changed = baseline.stream().anyMatch(old -> old.code().equals(warning.code())
                && (old.path().equals(warning.path()) || identity(context.existingScene().json(), old.path()).equals(source)));
            item.addProperty("origin", existed ? "pre_existing" : changed ? "changed" : "new");
            item.addProperty("blocking", false);
            result.add(item);
        }
        return result;
    }

    static JsonObject warningSummary(JsonArray warnings) {
        int highPriority = 0, newOrChanged = 0;
        for (JsonElement entry : warnings) {
            JsonObject warning = entry.getAsJsonObject();
            if ("high".equals(warning.get("priority").getAsString())) highPriority++;
            if (!"pre_existing".equals(warning.get("origin").getAsString())) newOrChanged++;
        }
        JsonObject summary = new JsonObject();
        summary.addProperty("total", warnings.size());
        summary.addProperty("high_priority", highPriority);
        summary.addProperty("new_or_changed", newOrChanged);
        summary.addProperty("blocking", false);
        summary.addProperty("recommended_action", warnings.isEmpty() ? "No quality warnings."
            : "Consider high-priority, new/changed and task-related pre-existing warnings before submission."
                + " Make useful in-scope repairs when feasible; briefly explain significant retained warnings."
                + " Unrelated pre-existing warnings do not authorize extra edits. Warnings never prevent acceptance.");
        return summary;
    }

    private static String identity(JsonObject scene, String path) {
        try {
            String pointer = path.replaceFirst("^\\$\\.?", "").replaceAll("\\[(\\d+)\\]", "/$1").replace('.', '/');
            JsonElement value = scene;
            String segment = "";
            for (String token : pointer.split("/")) {
                if (token.isEmpty()) continue;
                value = value.isJsonArray() ? value.getAsJsonArray().get(Integer.parseInt(token)) : value.getAsJsonObject().get(token);
                if (value == null) break;
                if (value.isJsonObject() && value.getAsJsonObject().has("id")) segment = value.getAsJsonObject().get("id").toString();
            }
            return segment + ":" + value;
        } catch (RuntimeException ignored) { return path; }
    }
}
