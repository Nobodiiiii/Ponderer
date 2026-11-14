package com.nododiiiii.ponderer.scene;

/** Immutable serialized document, independent of mutable editor drafts and runtime objects. */
public record SceneSnapshot(String json, String sourceFile) {
    public static SceneSnapshot of(DslScene document) {
        return new SceneSnapshot(SceneCodec.encode(document), document.sourceFile);
    }
    public DslScene document() {
        DslScene document = SceneCodec.decode(json);
        document.sourceFile = sourceFile;
        return document;
    }
}
