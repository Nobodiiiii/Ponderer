package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;

public class RotateCameraScreen extends AbstractStepEditorScreen {

    private final StepTextFieldHandle degreesField = new StepTextFieldHandle("degrees");
    private final StepTextFieldHandle durationField = new StepTextFieldHandle("duration");

    public RotateCameraScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.rotate_camera.add"), scene, sceneIndex, parent);
    }

    public RotateCameraScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                              int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.rotate_camera.edit"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override
    protected String getHeaderTitle() { return UIText.of("ponderer.ui.rotate_camera"); }

    @Override
    protected void collectStepEntries(List<com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.number(
            degreesField,
            "ponderer.ui.rotate_camera.degrees",
            "ponderer.ui.rotate_camera.degrees.tooltip",
            "90",
            60,
            "ponderer.ui.rotate_camera.degrees.unit"));
        entries.add(FieldSpecs.ticksNumber(
            durationField,
            "ponderer.ui.duration",
            "ponderer.ui.rotate_camera.duration.tooltip",
            "20",
            60));
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.degrees != null) {
            degreesField.setValue(String.valueOf(step.degrees));
        }
        if (step.duration != null) {
            durationField.setValue(String.valueOf(step.duration));
        }
    }

    @Override
    protected String getStepType() { return "rotate_camera_y"; }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;
        String raw = degreesField.getValue() == null ? "" : degreesField.getValue().trim();
        raw = raw.replaceAll("[^0-9+\\-\\.]", "").trim();
        if (raw.isEmpty()) {
            raw = "90";
        }

        Float degrees = parseFloat(raw, "Degrees");
        if (degrees == null) {
            return null;
        }

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "rotate_camera_y";
        s.degrees = degrees;
        s.duration = parseIntOr(durationField.getValue(), 20);
        if (s.duration < 0) {
            errorMessage = UIText.of("ponderer.ui.rotate_camera.error.duration");
            return null;
        }
        return s;
    }
}
