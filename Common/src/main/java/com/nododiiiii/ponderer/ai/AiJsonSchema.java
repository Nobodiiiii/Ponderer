package com.nododiiiii.ponderer.ai;

import com.google.gson.*;

/** Validates the schema subset used by the local tool registry, independent of provider strict mode. */
final class AiJsonSchema {
    private AiJsonSchema() {}

    static void validate(JsonObject schema, JsonElement value, String path) {
        if (schema.has("anyOf")) {
            boolean valid = false;
            for (JsonElement branch : schema.getAsJsonArray("anyOf")) {
                try { validate(branch.getAsJsonObject(), value, path); valid = true; break; }
                catch (IllegalArgumentException ignored) { }
            }
            if (!valid) fail(path, "does not match an allowed value shape");
        }
        if (schema.has("type")) {
            String type = schema.get("type").getAsString();
            boolean valid = switch (type) {
                case "object" -> value != null && value.isJsonObject();
                case "array" -> value != null && value.isJsonArray();
                case "string" -> value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
                case "boolean" -> value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
                case "number", "integer" -> value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                    && (!"integer".equals(type) || value.getAsBigDecimal().stripTrailingZeros().scale() <= 0);
                default -> throw new IllegalArgumentException("Unsupported host schema type: " + type);
            };
            if (!valid) fail(path, "expected " + type);
        }
        if (schema.has("enum")) {
            boolean found = false;
            for (JsonElement option : schema.getAsJsonArray("enum")) if (option.equals(value)) found = true;
            if (!found) fail(path, "expected one of " + schema.get("enum"));
        }
        if (value == null || value.isJsonNull()) return;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (schema.has("minProperties") && object.size() < schema.get("minProperties").getAsInt()) fail(path, "too few object fields");
            if (schema.has("required")) for (JsonElement key : schema.getAsJsonArray("required")) {
                if (!object.has(key.getAsString())) fail(path + "/" + key.getAsString(), "required field missing");
            }
            JsonObject properties = schema.has("properties") ? schema.getAsJsonObject("properties") : new JsonObject();
            for (var entry : object.entrySet()) {
                if (properties.has(entry.getKey())) {
                    validate(properties.getAsJsonObject(entry.getKey()), entry.getValue(), path + "/" + entry.getKey());
                } else if (schema.get("additionalProperties") instanceof JsonObject extra) {
                    validate(extra, entry.getValue(), path + "/" + entry.getKey());
                } else if (schema.has("properties") && (!schema.has("additionalProperties") || !schema.get("additionalProperties").getAsBoolean())) {
                    fail(path + "/" + entry.getKey(), "unknown field");
                }
            }
        } else if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            if (schema.has("minItems") && array.size() < schema.get("minItems").getAsInt()) fail(path, "too few array items");
            if (schema.has("maxItems") && array.size() > schema.get("maxItems").getAsInt()) fail(path, "too many array items");
            if (schema.has("items")) for (int i = 0; i < array.size(); i++) validate(schema.getAsJsonObject("items"), array.get(i), path + "/" + i);
        } else if (value.getAsJsonPrimitive().isString()) {
            if (schema.has("minLength") && value.getAsString().length() < schema.get("minLength").getAsInt()) fail(path, "string is too short");
        } else if (value.getAsJsonPrimitive().isNumber()) {
            if (schema.has("minimum") && value.getAsBigDecimal().compareTo(schema.get("minimum").getAsBigDecimal()) < 0) fail(path, "below minimum");
            if (schema.has("maximum") && value.getAsBigDecimal().compareTo(schema.get("maximum").getAsBigDecimal()) > 0) fail(path, "above maximum");
        }
    }

    private static void fail(String path, String message) { throw new IllegalArgumentException("INVALID_TOOL_ARGUMENTS at " + path + ": " + message); }
}
