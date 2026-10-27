package com.nododiiiii.ponderer.editor.screen;

import com.nododiiiii.ponderer.scene.DslScene;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public record NbtExpandedEditorContext(
    @Nullable DslScene scene,
    int sceneIndex,
    @Nullable SceneEditorScreen parent,
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
