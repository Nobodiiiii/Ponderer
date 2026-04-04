package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;

public class IndicateEffectScreen extends AbstractStepEditorScreen {

    private final String stepType;
    private final StepXyzFieldHandle posField = new StepXyzFieldHandle("pos");

    public IndicateEffectScreen(String stepType, DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui." + stepType + ".add"), scene, sceneIndex, parent);
        this.stepType = stepType;
    }

    public IndicateEffectScreen(String stepType, DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui." + stepType + ".edit"), scene, sceneIndex, parent, editIndex, step);
        this.stepType = stepType;
    }

    @Override
    protected String getHeaderTitle() { return UIText.of("ponderer.ui." + stepType); }

    @Override
    protected void collectFormEntries(List<StepEditorEntry> entries) {
        entries.add(StepEditorEntries.xyz(
            posField,
            "ponderer.ui." + stepType + ".pos",
            "ponderer.ui." + stepType + ".pos.tooltip",
            PickState.TargetField.POS1));
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.blockPos != null && step.blockPos.size() >= 3) {
            posField.setValue(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
        }
    }

    @Override
    protected String getStepType() { return stepType; }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;

        Integer px = parseInt(posField.x(), "X");
        Integer py = parseInt(posField.y(), "Y");
        Integer pz = parseInt(posField.z(), "Z");
        if (px == null || py == null || pz == null) return null;

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = stepType;
        s.blockPos = List.of(px, py, pz);
        return s;
    }
}
