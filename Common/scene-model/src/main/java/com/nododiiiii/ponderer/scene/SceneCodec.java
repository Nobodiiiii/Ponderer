package com.nododiiiii.ponderer.scene;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import javax.annotation.Nullable;

/** Existing JSON format, including localized text and lenient legacy reads. */
public final class SceneCodec {
    private static final Gson READER = builder().setLenient().create();
    private static final Gson WRITER = builder().setPrettyPrinting().create();
    private SceneCodec() {}
    private static GsonBuilder builder() {
        return new GsonBuilder().disableHtmlEscaping()
            .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter());
    }
    public static DslScene decode(String json) { return withAnchors(READER.fromJson(json, DslScene.class)); }
    public static DslScene decode(Reader json) { return withAnchors(READER.fromJson(json, DslScene.class)); }
    public static String encode(DslScene scene) { return READER.toJson(withAnchors(scene)); }
    public static String pretty(DslScene scene) { return WRITER.toJson(withAnchors(scene)); }
    public static String encodeText(LocalizedText text) { return READER.toJson(text, LocalizedText.class); }
    public static LocalizedText decodeText(String json) { return READER.fromJson(json, LocalizedText.class); }
    /** Legacy documents receive deterministic anchors; once saved they survive reorder and edits. */
    public static DslScene withAnchors(DslScene scene) {
        if (scene == null) return null;
        if (scene.internalId == null || scene.internalId.isBlank()) scene.internalId = scene.identity();
        if (scene.scenes == null) return scene;
        for (var segment : scene.scenes) {
            if (segment == null || segment.steps == null) continue;
            withStepAnchors(segment.steps);
        }
        return scene;
    }
    private static void withStepAnchors(java.util.List<DslScene.DslStep> steps) {
        java.util.Set<String> used = new java.util.HashSet<>();
        for (var step : steps) if (step != null && step.id != null && !step.id.isBlank()) used.add(step.id);
        for (int i = 0; i < steps.size(); i++) {
            var step = steps.get(i);
            if (step == null) continue;
            if (step.id == null || step.id.isBlank()) {
                String id = "legacy_" + i;
                while (!used.add(id)) id += "_";
                step.id = id;
            }
            if ("interface_panel".equalsIgnoreCase(step.type) && step.steps != null) withStepAnchors(step.steps);
        }
    }
    @Nullable
    public static DslScene copy(@Nullable DslScene scene) {
        if (scene == null) return null;
        DslScene copy = decode(encode(scene));
        copy.javaSourceRevision = scene.javaSourceRevision;
        return copy;
    }
    /** A detached document retaining origin metadata, which is deliberately absent from JSON. */
    public static DslScene snapshot(DslScene scene) {
        DslScene copy = copy(scene);
        copy.sourceFile = scene.sourceFile;
        return copy;
    }
}
