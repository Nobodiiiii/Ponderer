package com.nododiiiii.ponderer.scene;

import com.google.gson.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

/** Retains extension fields even when the Java provider is not installed in the authoring instance. */
public final class StepJsonAdapter implements JsonSerializer<DslScene.DslStep>, JsonDeserializer<DslScene.DslStep> {
    @Override public DslScene.DslStep deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
        DslScene.DslStep step = new DslScene.DslStep();
        JsonObject remaining = json.getAsJsonObject().deepCopy();
        try {
            for (Field field : DslScene.DslStep.class.getFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) continue;
                var annotation = field.getAnnotation(com.google.gson.annotations.SerializedName.class);
                String name = annotation == null ? field.getName() : annotation.value();
                JsonElement value = remaining.remove(name);
                if (annotation != null) for (String alternate : annotation.alternate()) {
                    JsonElement alias = remaining.remove(alternate);
                    if (value == null) value = alias;
                }
                if (value != null && (!value.isJsonNull() || !field.getType().isPrimitive())) field.set(step, context.deserialize(value, field.getGenericType()));
            }
        } catch (IllegalAccessException error) { throw new JsonParseException(error); }
        step.extensionData = remaining;
        return step;
    }
    @Override public JsonElement serialize(DslScene.DslStep step, Type type, JsonSerializationContext context) {
        JsonObject json = step.extensionData == null ? new JsonObject() : step.extensionData.deepCopy();
        try {
            for (Field field : DslScene.DslStep.class.getFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) continue;
                Object value = field.get(step);
                var annotation = field.getAnnotation(com.google.gson.annotations.SerializedName.class);
                String name = annotation == null ? field.getName() : annotation.value();
                if (value == null) json.remove(name);
                else json.add(name, context.serialize(value, field.getGenericType()));
            }
        } catch (IllegalAccessException error) { throw new JsonParseException(error); }
        return json;
    }
}
