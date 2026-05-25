package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

public record NbtExpandedEditorContext(
    DslScene scene,
    int sceneIndex,
    SceneEditorScreen parent,
    SnapshotReturnContext parentContext,
    Map<String, String> parentSnapshot,
    String targetSnapshotKey
) implements SnapshotReturnContext {

    public NbtExpandedEditorContext {
        parentSnapshot = new HashMap<>(parentSnapshot);
    }

    @Override
    public void reopenEditor(Map<String, String> snapshot) {
        Minecraft.getInstance().setScreen(
            new NbtExpandedEditorScreen(scene, sceneIndex, parent, parentContext, parentSnapshot, targetSnapshotKey)
                .setPendingFormRestore(snapshot));
    }
}
