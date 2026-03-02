package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SectionTransformScreen extends AbstractStepEditorScreen {

    private final String stepType;
    private final boolean rotationMode;

    private HintableTextFieldWidget linkIdField;
    private HintableTextFieldWidget xField, yField, zField;
    private HintableTextFieldWidget durationField;

    public SectionTransformScreen(String stepType, boolean rotationMode,
                                  DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui." + stepType + ".add"), scene, sceneIndex, parent);
        this.stepType = stepType;
        this.rotationMode = rotationMode;
    }

    public SectionTransformScreen(String stepType, boolean rotationMode,
                                  DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                  int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui." + stepType + ".edit"), scene, sceneIndex, parent, editIndex, step);
        this.stepType = stepType;
        this.rotationMode = rotationMode;
    }

    @Override
    protected int getFormRowCount() { return 3; }

    @Override
    protected String getHeaderTitle() { return UIText.of("ponderer.ui." + stepType); }

    @Override
    protected void buildForm() {
        beginForm();
        linkIdField = addFormTextField("ponderer.ui." + stepType + ".link", "ponderer.ui." + stepType + ".link.tooltip", "default", 140);
        var xyz = addFormXyzRow("ponderer.ui." + stepType + ".xyz", "ponderer.ui." + stepType + ".xyz.tooltip");
        xField = xyz.x();
        yField = xyz.y();
        zField = xyz.z();
        durationField = addFormNumberField("ponderer.ui.duration", "ponderer.ui.duration.tooltip.idle", "20", 60);
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.linkId != null) linkIdField.setValue(step.linkId);
        if (rotationMode) {
            if (step.rotX != null) xField.setValue(String.valueOf(step.rotX));
            if (step.rotY != null) yField.setValue(String.valueOf(step.rotY));
            if (step.rotZ != null) zField.setValue(String.valueOf(step.rotZ));
        } else if (step.offset != null && step.offset.size() >= 3) {
            xField.setValue(String.valueOf(step.offset.get(0)));
            yField.setValue(String.valueOf(step.offset.get(1)));
            zField.setValue(String.valueOf(step.offset.get(2)));
        }
        if (step.duration != null) durationField.setValue(String.valueOf(step.duration));
    }

    @Override
    protected String getStepType() { return stepType; }

    @Override
    protected Map<String, String> snapshotForm() {
        Map<String, String> m = new HashMap<>();
        m.put("linkId", linkIdField.getValue());
        m.put("x", xField.getValue());
        m.put("y", yField.getValue());
        m.put("z", zField.getValue());
        m.put("duration", durationField.getValue());
        return m;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        if (snapshot.containsKey("linkId")) linkIdField.setValue(snapshot.get("linkId"));
        if (snapshot.containsKey("x")) xField.setValue(snapshot.get("x"));
        if (snapshot.containsKey("y")) yField.setValue(snapshot.get("y"));
        if (snapshot.containsKey("z")) zField.setValue(snapshot.get("z"));
        if (snapshot.containsKey("duration")) durationField.setValue(snapshot.get("duration"));
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;

        Double x = parseDouble(xField.getValue(), "X");
        Double y = parseDouble(yField.getValue(), "Y");
        Double z = parseDouble(zField.getValue(), "Z");
        if (x == null || y == null || z == null) return null;

        int duration = parseIntOr(durationField.getValue(), 20);

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = stepType;
        String link = linkIdField.getValue().trim();
        s.linkId = link.isEmpty() ? "default" : link;
        s.duration = Math.max(0, duration);

        if (rotationMode) {
            s.rotX = x.floatValue();
            s.rotY = y.floatValue();
            s.rotZ = z.floatValue();
        } else {
            s.offset = List.of(x, y, z);
        }

        return s;
    }
}
