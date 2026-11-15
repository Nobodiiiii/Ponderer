package com.nododiiiii.ponderer.exporter;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.nododiiiii.ponderer.scene.LocalizedText;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.stream.Collectors;

/** Writes scene data as Java statements; raw extension JSON remains data, never executable source. */
final class JavaValueEmitter {
    private static final Gson STRINGS = new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
    private final StringBuilder statements = new StringBuilder();
    private final boolean shortNames;
    private final String textScope;
    private int next;
    JavaValueEmitter() { this(false); }
    JavaValueEmitter(boolean shortNames) { this(shortNames, null); }
    JavaValueEmitter(boolean shortNames, String textScope) { this.shortNames = shortNames; this.textScope = textScope; }
    private String type(String name) {
        if (!shortNames) return name;
        return name.replace("com.nododiiiii.ponderer.scene.DslScene", "DslScene")
            .replace("com.nododiiiii.ponderer.scene.LocalizedText", "LocalizedText")
            .replace("java.util.Arrays", "Arrays").replace("java.util.Map", "Map")
            .replace("com.google.gson.JsonParser", "JsonParser")
            .replace("com.google.gson.JsonObject", "JsonObject");
    }
    String statements() { return statements.toString(); }
    String value(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return STRINGS.toJson(s);
        if (value instanceof Boolean || value instanceof Integer || value instanceof Short || value instanceof Byte) return value.toString();
        if (value instanceof Float f) return Float.toString(f) + "f";
        if (value instanceof Double d) return Double.toString(d) + "d";
        if (value instanceof Long l) return l + "L";
        if (value instanceof Enum<?> e) return e.getDeclaringClass().getCanonicalName() + "." + e.name();
        if (value instanceof JsonElement json) {
            if (json.isJsonObject() && json.getAsJsonObject().isEmpty()) return "new " + type("com.google.gson.JsonObject") + "()";
            return type("com.google.gson.JsonParser") + ".parseString(" + STRINGS.toJson(json.toString()) + ")"
            + (json.isJsonObject() ? ".getAsJsonObject()" : json.isJsonArray() ? ".getAsJsonArray()" : "");
        }
        if (value instanceof LocalizedText text && text.isReference()) {
            if (text.resource().equals(textScope)) return "text(" + value(text.key()) + ")";
            return type("com.nododiiiii.ponderer.scene.LocalizedText") + ".ref(" + value(text.resource()) + ", " + value(text.key()) + ")";
        }
        if (value instanceof LocalizedText text) return type("com.nododiiiii.ponderer.scene.LocalizedText") + "."
            + (text.isPlain() ? "of(" + value(text.resolve()) : "ofMap(" + value(text.getAllTranslations())) + ")";
        if (value instanceof List<?> list) return (shortNames && list.stream().noneMatch(Objects::isNull) ? "List.of(" : type("java.util.Arrays") + ".asList(")
            + list.stream().map(this::value).collect(Collectors.joining(", ")) + ")";
        if (value instanceof Map<?, ?> map) return type("java.util.Map") + ".ofEntries(" + map.entrySet().stream()
            .sorted(java.util.Comparator.comparing(entry -> String.valueOf(entry.getKey())))
            .map(entry -> type("java.util.Map") + ".entry(" + value(entry.getKey()) + ", " + value(entry.getValue()) + ")")
            .collect(Collectors.joining(", ")) + ")";
        String simple = value.getClass().getSimpleName();
        String name = Character.toLowerCase(simple.charAt(0)) + simple.substring(1) + next++;
        String type = type(value.getClass().getCanonicalName());
        statements.append("        ").append(type).append(' ').append(name).append(" = new ").append(type).append("();\n");
        fields(name, value, Set.of());
        return name;
    }
    void fields(String name, Object value, Set<String> omitted) {
        try {
            Object defaults = value.getClass().getConstructor().newInstance();
            for (var field : value.getClass().getFields()) {
                if (Modifier.isStatic(field.getModifiers()) || omitted.contains(field.getName())) continue;
                // Extension fields are transient only because StepJsonAdapter flattens them into JSON.
                // Native Java documents must carry the same opaque payload across export and editing.
                if (Modifier.isTransient(field.getModifiers())
                    && !(value instanceof com.nododiiiii.ponderer.scene.DslScene.DslStep && field.getName().equals("extensionData"))) continue;
                Object fieldValue = field.get(value);
                if (Objects.equals(fieldValue, field.get(defaults))) continue;
                if (fieldValue instanceof JsonElement json && json.isJsonObject() && json.getAsJsonObject().isEmpty()
                    && field.getName().equals("extensionData")) continue;
                String expression = value(fieldValue);
                statements.append("        ").append(name).append('.').append(field.getName()).append(" = ").append(expression).append(";\n");
            }
        } catch (ReflectiveOperationException error) { throw new IllegalArgumentException("Cannot emit " + value.getClass(), error); }
    }
}
