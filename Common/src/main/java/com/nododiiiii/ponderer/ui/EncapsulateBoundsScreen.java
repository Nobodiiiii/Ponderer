package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Editor for "encapsulate_bounds" step.
 * Fields: bounds X, Y, Z (3 integers defining the bounding box size).
 */
public class EncapsulateBoundsScreen extends AbstractStepEditorScreen {

    private final StepXyzFieldHandle boundsField = new StepXyzFieldHandle("bounds");

    public EncapsulateBoundsScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.encapsulate_bounds"), scene, sceneIndex, parent);
    }

    public EncapsulateBoundsScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                   int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.encapsulate_bounds"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override protected String getHeaderTitle() { return UIText.of("ponderer.ui.encapsulate_bounds"); }

    @Override
    protected void collectFormEntries(List<StepEditorEntry> entries) {
        entries.add(StepEditorEntries.xyz(
            boundsField,
            "ponderer.ui.encapsulate_bounds.bounds",
            "ponderer.ui.encapsulate_bounds.bounds.tooltip"));
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.bounds != null && step.bounds.size() >= 3) {
            boundsField.setValue(step.bounds.get(0), step.bounds.get(1), step.bounds.get(2));
        }
    }

    @Override
    protected String getStepType() { return "encapsulate_bounds"; }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;
        Integer bx = parseInt(boundsField.x(), "X");
        Integer by = parseInt(boundsField.y(), "Y");
        Integer bz = parseInt(boundsField.z(), "Z");
        if (bx == null || by == null || bz == null) return null;

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "encapsulate_bounds";
        s.bounds = List.of(bx, by, bz);
        return s;
    }
}
