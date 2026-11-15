package com.nododiiiii.ponderer.runtime.api;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** One registry per engine; content from different hosts does not overwrite registrations. */
public final class StepRegistry {
    private final Map<String, Object> capabilities = new ConcurrentHashMap<>();
    private final Map<String, StepType<?>> types = new ConcurrentHashMap<>();

    public AutoCloseable register(String id, StepType<?> type) {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Custom step needs a namespaced id: " + id);
        }
        Objects.requireNonNull(type);
        if (types.putIfAbsent(id, type) != null) throw new IllegalArgumentException("Duplicate step: " + id);
        return () -> types.remove(id, type);
    }

    public AutoCloseable provideCapability(String id) {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Capability needs a namespaced id: " + id);
        if (id.startsWith("pondererlib:")) throw new IllegalArgumentException("Core capabilities cannot be overridden: " + id);
        Object token = new Object();
        if (capabilities.putIfAbsent(id, token) != null) throw new IllegalArgumentException("Duplicate capability: " + id);
        return () -> capabilities.remove(id, token);
    }
    public boolean providesCapability(String id) { return capabilities.containsKey(id); }
    public void clear() { types.clear(); capabilities.clear(); }

    public boolean contains(String id) { return types.containsKey(id); }
    public java.util.List<String> editableTypes() {
        return types.entrySet().stream().filter(entry -> entry.getValue().editableParameters())
            .map(Map.Entry::getKey).sorted().toList();
    }
    public boolean editableParameters(String id) {
        StepType<?> type = types.get(id);
        return type != null && type.editableParameters();
    }
    public String editorLabel(String id) {
        StepType<?> type = types.get(id);
        return type == null || type.editorLabel().isBlank() ? id : type.editorLabel();
    }
    /** Compiled callbacks execute normally and appear as opaque Java steps in the optional editor. */
    public AutoCloseable registerJava(String id, String label, java.util.function.Consumer<StepBuildContext> body) {
        Objects.requireNonNull(body);
        Objects.requireNonNull(label);
        return register(id, new StepType<JsonObject>() {
            @Override public StepCodec<JsonObject> codec() { return new StepCodec<>() {
                public JsonObject decode(JsonObject data) { return data.deepCopy(); }
                public JsonObject encode(JsonObject data) { return data.deepCopy(); }
            }; }
            @Override public boolean editableParameters() { return false; }
            @Override public String editorLabel() { return label; }
            @Override public void build(JsonObject parameters, StepBuildContext context) { body.accept(context); }
        });
    }
    /** Validate edited parameters through the provider without constructing or executing instructions. */
    public void validateParameters(String id, JsonObject parameters) {
        StepType<?> type = types.get(id);
        if (type == null) throw new IllegalArgumentException("Unknown step " + id);
        type.codec().decode(parameters.deepCopy());
    }

    public void build(String id, JsonObject parameters, StepBuildContext context) {
        StepType<?> type = types.get(id);
        if (type == null) throw new IllegalArgumentException("Unknown step " + id + " in scene " + context.sceneId() + ", step " + context.stepId());
        try { buildTyped(type, parameters == null ? new JsonObject() : parameters.deepCopy(), context); }
        catch (RuntimeException error) { throw new IllegalArgumentException(context.sceneId() + "/" + context.stepId() + ": custom step " + id + ": " + error.getMessage(), error); }
    }

    private static <P> void buildTyped(StepType<P> type, JsonObject parameters, StepBuildContext context) {
        type.build(type.codec().decode(parameters), context);
    }
}
