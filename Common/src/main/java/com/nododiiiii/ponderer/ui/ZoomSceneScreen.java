package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;

public class ZoomSceneScreen extends AbstractStepEditorScreen {

    private final StepXyzFieldHandle centerField = new StepXyzFieldHandle("point");
    private final StepTextFieldHandle scaleField = new StepTextFieldHandle("scale");
    private final StepTextFieldHandle durationField = new StepTextFieldHandle("duration");

    public ZoomSceneScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.zoom_scene.add"), scene, sceneIndex, parent);
    }

    public ZoomSceneScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                           int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.zoom_scene.edit"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override
    protected String getHeaderTitle() { return UIText.of("ponderer.ui.zoom_scene"); }

    @Override
    protected void collectStepEntries(List<com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry> entries) {
        entries.add(StepEditorEntries.xyz(
            centerField,
            "ponderer.ui.zoom_scene.center",
            "ponderer.ui.zoom_scene.center.tooltip",
            PickState.TargetField.POINT,
            true));
        entries.add(StepEditorEntries.number(
            scaleField,
            "ponderer.ui.zoom_scene.scale",
            "ponderer.ui.zoom_scene.scale.tooltip",
            "1.0",
            60,
            null));
        entries.add(StepEditorEntries.number(
            durationField,
            "ponderer.ui.duration",
            "ponderer.ui.zoom_scene.duration.tooltip",
            "20",
            60,
            null));
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.point != null && step.point.size() >= 3) {
            centerField.setValue(step.point.get(0), step.point.get(1), step.point.get(2));
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

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;

        Double x = parseOptionalDouble(centerField.x(), "Center X");
        Double y = parseOptionalDouble(centerField.y(), "Center Y");
        Double z = parseOptionalDouble(centerField.z(), "Center Z");
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
