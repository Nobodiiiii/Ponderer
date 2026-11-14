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
