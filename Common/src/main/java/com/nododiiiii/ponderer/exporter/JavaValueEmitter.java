package com.nododiiiii.ponderer.exporter;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.nododiiiii.ponderer.scene.LocalizedText;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Writes scene data as Java statements; raw extension JSON remains data, never executable source. */
final class JavaValueEmitter {
    private static final Gson STRINGS = new Gson();
    private final StringBuilder statements = new StringBuilder();
    private int next;
    String statements() { return statements.toString(); }
    String value(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return STRINGS.toJson(s);
        if (value instanceof Boolean || value instanceof Integer || value instanceof Short || value instanceof Byte) return value.toString();
        if (value instanceof Float f) return Float.toString(f) + "f";
        if (value instanceof Double d) return Double.toString(d) + "d";
        if (value instanceof Long l) return l + "L";
        if (value instanceof Enum<?> e) return e.getDeclaringClass().getCanonicalName() + "." + e.name();
        if (value instanceof JsonElement json) return "com.google.gson.JsonParser.parseString(" + STRINGS.toJson(json.toString()) + ")"
            + (json.isJsonObject() ? ".getAsJsonObject()" : json.isJsonArray() ? ".getAsJsonArray()" : "");
        if (value instanceof LocalizedText text) return "com.nododiiiii.ponderer.scene.LocalizedText."
            + (text.isPlain() ? "of(" + value(text.resolve()) : "ofMap(" + value(text.getAllTranslations())) + ")";
        if (value instanceof List<?> list) return "java.util.Arrays.asList(" + list.stream().map(this::value).collect(Collectors.joining(", ")) + ")";
        if (value instanceof Map<?, ?> map) return "java.util.Map.ofEntries(" + map.entrySet().stream()
            .map(entry -> "java.util.Map.entry(" + value(entry.getKey()) + ", " + value(entry.getValue()) + ")")
            .collect(Collectors.joining(", ")) + ")";
        String name = "data" + next++;
        String type = value.getClass().getCanonicalName();
        statements.append("        ").append(type).append(' ').append(name).append(" = new ").append(type).append("();\n");
        try {
            for (var field : value.getClass().getFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                // Extension fields are transient only because StepJsonAdapter flattens them into JSON.
                // Native Java documents must carry the same opaque payload across export and editing.
                if (Modifier.isTransient(field.getModifiers())
                    && !(value instanceof com.nododiiiii.ponderer.scene.DslScene.DslStep && field.getName().equals("extensionData"))) continue;
                Object fieldValue = field.get(value);
                if (fieldValue == null) continue;
                String expression = value(fieldValue);
                statements.append("        ").append(name).append('.').append(field.getName()).append(" = ").append(expression).append(";\n");
            }
        } catch (IllegalAccessException error) { throw new IllegalArgumentException("Cannot emit " + type, error); }
        return name;
    }
}
