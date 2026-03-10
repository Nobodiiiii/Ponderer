package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZoomSceneScreen extends AbstractStepEditorScreen {

    private HintableTextFieldWidget pointXField, pointYField, pointZField;
    private HintableTextFieldWidget scaleField;
    private HintableTextFieldWidget durationField;

    public ZoomSceneScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.zoom_scene.add"), scene, sceneIndex, parent);
    }

    public ZoomSceneScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                           int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.zoom_scene.edit"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override
    protected int getFormRowCount() { return 3; }

    @Override
    protected String getHeaderTitle() { return UIText.of("ponderer.ui.zoom_scene"); }

    @Override
    protected void buildForm() {
        beginForm();
        var center = addFormXyzRow("ponderer.ui.zoom_scene.center", "ponderer.ui.zoom_scene.center.tooltip", PickState.TargetField.POINT, true);
        pointXField = center.x();
        pointYField = center.y();
        pointZField = center.z();
        scaleField = addFormNumberField("ponderer.ui.zoom_scene.scale", "ponderer.ui.zoom_scene.scale.tooltip", "1.0", 60);
        durationField = addFormNumberField("ponderer.ui.duration", "ponderer.ui.zoom_scene.duration.tooltip", "20", 60);
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.point != null && step.point.size() >= 3) {
            pointXField.setValue(String.valueOf(step.point.get(0)));
            pointYField.setValue(String.valueOf(step.point.get(1)));
            pointZField.setValue(String.valueOf(step.point.get(2)));
        }
        if (step.scale != null) {
            scaleField.setValue(String.valueOf(step.scale));
        }
        if (step.duration != null) {
            durationField.setValue(String.valueOf(step.duration));
        }
    }

    @Override
    protected String getStepType() { return "zoom_scene"; }

    @Override
    protected Map<String, String> snapshotForm() {
        Map<String, String> m = new HashMap<>();
        m.put("pointX", pointXField.getValue());
        m.put("pointY", pointYField.getValue());
        m.put("pointZ", pointZField.getValue());
        m.put("scale", scaleField.getValue());
        m.put("duration", durationField.getValue());
        return m;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        if (snapshot.containsKey("pointX")) pointXField.setValue(snapshot.get("pointX"));
        if (snapshot.containsKey("pointY")) pointYField.setValue(snapshot.get("pointY"));
        if (snapshot.containsKey("pointZ")) pointZField.setValue(snapshot.get("pointZ"));
        if (snapshot.containsKey("scale")) scaleField.setValue(snapshot.get("scale"));
        if (snapshot.containsKey("duration")) durationField.setValue(snapshot.get("duration"));
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;

        Double x = parseOptionalDouble(pointXField.getValue(), "Center X");
        Double y = parseOptionalDouble(pointYField.getValue(), "Center Y");
        Double z = parseOptionalDouble(pointZField.getValue(), "Center Z");
        boolean hasCenter = x != null || y != null || z != null;
        if (hasCenter && (x == null || y == null || z == null)) {
            errorMessage = UIText.of("ponderer.ui.zoom_scene.error.partial_center");
            return null;
        }

        Float scale = null;
        String scaleRaw = scaleField.getValue() == null ? "" : scaleField.getValue().trim();
        if (!scaleRaw.isEmpty()) {
            scale = parseFloat(scaleRaw, "Scale");
            if (scale == null) return null;
            if (scale <= 0) {
                errorMessage = UIText.of("ponderer.ui.zoom_scene.error.scale_positive");
                return null;
            }
        }

        int duration = Math.max(0, parseIntOr(durationField.getValue(), 20));

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "zoom_scene";
        if (hasCenter) s.point = List.of(x, y, z);
        s.scale = scale;
        s.duration = duration;
        return s;
    }

    @Nullable
    private Double parseOptionalDouble(String raw, String label) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) return null;
        return parseDouble(trimmed, label);
    }
}
