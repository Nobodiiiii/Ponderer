package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.client.Minecraft;

import java.util.Map;

public record TriggerEditorContext(DslScene scene, int sceneIndex, SceneEditorScreen parent)
    implements SnapshotReturnContext {

    @Override
    public void reopenEditor(Map<String, String> snapshot) {
        Minecraft.getInstance().setScreen(new TriggerEditorScreen(scene, sceneIndex, parent).setPendingFormRestore(snapshot));
    }
}
