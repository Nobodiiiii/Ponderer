package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Shared context for workflows that leave the current editor screen and later
 * reopen the same step editor with a restored snapshot.
 */
public record StepEditorContext(
    String stepType,
    int editIndex,
    int insertAfterIndex,
    DslScene scene,
    int sceneIndex,
    SceneEditorScreen parent
) implements SnapshotReturnContext {

    @Nullable
    public AbstractStepEditorScreen createEditor() {
        if (editIndex >= 0) {
            List<DslScene.DslStep> steps = getStepsForScene(scene, sceneIndex);
            DslScene.DslStep existingStep = (steps != null && editIndex < steps.size()) ? steps.get(editIndex) : null;
            return StepEditorFactory.createEditScreen(existingStep, editIndex, scene, sceneIndex, parent);
        }
        return StepEditorFactory.createAddScreen(stepType, scene, sceneIndex, parent);
    }

    @Override
    public void reopenEditor(Map<String, String> snapshot) {
        AbstractStepEditorScreen editor = createEditor();
        if (editor == null) {
            snapshot.clear();
            return;
        }
        editor.setInsertAfterIndex(insertAfterIndex);
        editor.setPendingPickRestore(snapshot);
        Minecraft.getInstance().setScreen(editor);
    }

    @Nullable
    public static List<DslScene.DslStep> getStepsForScene(@Nullable DslScene scene, int sceneIndex) {
        if (scene == null || scene.scenes == null || scene.scenes.isEmpty()) {
            return null;
        }
        if (sceneIndex < 0 || sceneIndex >= scene.scenes.size()) {
            return null;
        }
        return scene.scenes.get(sceneIndex).steps;
    }
}
