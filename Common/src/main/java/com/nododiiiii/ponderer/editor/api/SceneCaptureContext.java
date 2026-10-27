package com.nododiiiii.ponderer.editor.api;

import com.nododiiiii.ponderer.scene.DslScene;

public interface SceneCaptureContext extends SnapshotReturnContext {
    DslScene scene();
    int sceneIndex();
    boolean openPreview();
}
