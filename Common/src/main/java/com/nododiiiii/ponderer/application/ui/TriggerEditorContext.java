package com.nododiiiii.ponderer.application.ui;

import com.nododiiiii.ponderer.editor.api.SnapshotReturnContext;
import com.nododiiiii.ponderer.scene.DslScene;
import net.minecraft.client.Minecraft;

import java.util.Map;

public record TriggerEditorContext(DslScene scene, int sceneIndex, SceneEditorScreen parent)
    implements SnapshotReturnContext {

    @Override
    public void reopenEditor(Map<String, String> snapshot) {
        Minecraft.getInstance().setScreen(new TriggerEditorScreen(scene, sceneIndex, parent).setPendingFormRestore(snapshot));
    }
}
