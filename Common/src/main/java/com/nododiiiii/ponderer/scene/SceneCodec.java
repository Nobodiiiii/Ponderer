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
    public static DslScene decode(String json) { return READER.fromJson(json, DslScene.class); }
    public static DslScene decode(Reader json) { return READER.fromJson(json, DslScene.class); }
    public static String encode(DslScene scene) { return READER.toJson(scene); }
    public static String pretty(DslScene scene) { return WRITER.toJson(scene); }
    @Nullable
    public static DslScene copy(@Nullable DslScene scene) {
        return scene == null ? null : decode(encode(scene));
    }
    /** A detached document retaining origin metadata, which is deliberately absent from JSON. */
    public static DslScene snapshot(DslScene scene) {
        DslScene copy = copy(scene);
        copy.sourceFile = scene.sourceFile;
        return copy;
    }
}
