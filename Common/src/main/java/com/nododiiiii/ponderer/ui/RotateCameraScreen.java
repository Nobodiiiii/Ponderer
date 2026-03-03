package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class RotateCameraScreen extends AbstractStepEditorScreen {

    private HintableTextFieldWidget degreesField;

    public RotateCameraScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.rotate_camera.add"), scene, sceneIndex, parent);
    }

    public RotateCameraScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                              int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.rotate_camera.edit"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override
    protected int getFormRowCount() { return 1; }

    @Override
    protected String getHeaderTitle() { return UIText.of("ponderer.ui.rotate_camera"); }

    @Override
    protected void buildForm() {
        beginForm();
        degreesField = addFormNumberField("ponderer.ui.rotate_camera.degrees", "ponderer.ui.rotate_camera.degrees.tooltip", "90", 60, "ponderer.ui.rotate_camera.degrees.unit");
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.degrees != null) {
            degreesField.setValue(String.valueOf(step.degrees));
        }
    }

    @Override
    protected String getStepType() { return "rotate_camera_y"; }

    @Override
    protected Map<String, String> snapshotForm() {
        Map<String, String> m = new HashMap<>();
        m.put("degrees", degreesField.getValue());
        return m;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        if (snapshot.containsKey("degrees")) degreesField.setValue(snapshot.get("degrees"));
    }

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
        return s;
    }
}
