package com.nododiiiii.ponderer.ai;

import com.google.gson.*;
import java.util.*;

/** Per-run transactionally updated draft. Never writes a scene to disk. */
final class AiDraftStore {
    private final String id = UUID.randomUUID().toString();
    private JsonObject scene;
    private int revision;

    AiDraftStore(JsonObject initial) { scene = initial.deepCopy(); }
    JsonObject scene() { return scene.deepCopy(); }
    int revision() { return revision; }

    JsonObject descriptor() {
        JsonObject value = new JsonObject();
        value.addProperty("draft_id", id);
        value.addProperty("revision", revision);
        return value;
    }

    void requireRevision(JsonObject arguments) {
        if (!arguments.has("draft_id") || !id.equals(arguments.get("draft_id").getAsString())
            || !arguments.has("revision") || arguments.get("revision").getAsInt() != revision) {
            throw new IllegalArgumentException("STALE_DRAFT: get_scene_draft and use its exact draft_id and revision");
        }
    }

    void replace(JsonObject value) {
        if (!scene.equals(value)) {
            scene = value.deepCopy();
            revision++;
        }
    }

    JsonObject patch(JsonArray operations) {
        if (operations.isEmpty() || operations.size() > 32) throw new IllegalArgumentException("Use 1 to 32 patch operations");
        JsonObject next = scene.deepCopy();
        for (JsonElement entry : operations) {
            JsonObject operation = entry.getAsJsonObject();
            String op = operation.get("op").getAsString();
            String path = operation.get("path").getAsString();
            if (!path.startsWith("/") || path.equals("/")) throw new IllegalArgumentException("A non-root JSON pointer is required");
            String[] tokens = path.substring(1).split("/", -1);
            for (int i = 0; i < tokens.length; i++) tokens[i] = tokens[i].replace("~1", "/").replace("~0", "~");
            if (Set.of("id", "pack", "editable", "items", "nbtFilter", "itemNbtFilters").contains(tokens[0])) {
                throw new IllegalArgumentException("HOST_FIELD: carrier and scene identity fields cannot be patched");
            }
            JsonElement parent = next;
            for (int i = 0; i < tokens.length - 1; i++) {
                parent = parent.isJsonArray() ? parent.getAsJsonArray().get(index(tokens[i], parent.getAsJsonArray().size(), false))
                    : parent.getAsJsonObject().get(tokens[i]);
                if (parent == null || parent.isJsonNull()) throw new IllegalArgumentException("PATCH_PATH: parent does not exist: " + path);
            }
            String key = tokens[tokens.length - 1];
            JsonElement previous;
            int position = -1;
            if (parent.isJsonArray()) {
                position = index(key, parent.getAsJsonArray().size(), "add".equals(op));
                previous = position < parent.getAsJsonArray().size() ? parent.getAsJsonArray().get(position) : null;
            } else previous = parent.getAsJsonObject().get(key);
            if (!Set.of("add", "replace", "remove").contains(op)) throw new IllegalArgumentException("Unsupported patch op: " + op);
            if (!"add".equals(op)) {
                if (previous == null || !operation.has("expected") || !previous.equals(operation.get("expected"))) {
                    throw new IllegalArgumentException("PATCH_CONFLICT: replace/remove needs the exact expected old value at " + path);
                }
            } else if (parent.isJsonObject() && previous != null) {
                throw new IllegalArgumentException("PATCH_CONFLICT: use replace with expected for an existing field: " + path);
            }
            if (!"remove".equals(op) && !operation.has("value")) throw new IllegalArgumentException("value is required");
            if (parent.isJsonArray()) {
                JsonArray array = parent.getAsJsonArray();
                if ("remove".equals(op)) array.remove(position);
                else if ("replace".equals(op)) array.set(position, operation.get("value").deepCopy());
                else {
                    // Gson has no insert; rebuild the array while preserving every existing value.
                    JsonArray rebuilt = new JsonArray();
                    for (int i = 0; i <= array.size(); i++) {
                        if (i == position) rebuilt.add(operation.get("value").deepCopy());
                        if (i < array.size()) rebuilt.add(array.get(i));
                    }
                    while (!array.isEmpty()) array.remove(array.size() - 1);
                    array.addAll(rebuilt);
                }
            } else if ("remove".equals(op)) parent.getAsJsonObject().remove(key);
            else parent.getAsJsonObject().add(key, operation.get("value").deepCopy());
        }
        return next;
    }

    private static int index(String token, int size, boolean insertion) {
        int result = "-".equals(token) && insertion ? size : Integer.parseInt(token);
        if (result < 0 || result > size || (!insertion && result == size)) throw new IllegalArgumentException("PATCH_PATH: array index out of range");
        return result;
    }
}
