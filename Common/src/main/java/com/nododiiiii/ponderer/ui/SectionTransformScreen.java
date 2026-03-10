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
    private HintableTextFieldWidget posXField, posYField, posZField;
    private HintableTextFieldWidget pos2XField, pos2YField, pos2ZField;
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
    protected int getFormRowCount() { return 5; }

    @Override
    protected String getHeaderTitle() { return UIText.of("ponderer.ui." + stepType); }

    @Override
    protected void buildForm() {
        beginForm();
        linkIdField = addFormTextField("ponderer.ui." + stepType + ".link", "ponderer.ui." + stepType + ".link.tooltip", "", 140);
        var from = addFormXyzRow("ponderer.ui." + stepType + ".pos_from", "ponderer.ui." + stepType + ".pos_from.tooltip", PickState.TargetField.POS1);
        posXField = from.x();
        posYField = from.y();
        posZField = from.z();
        var to = addFormXyzRow("ponderer.ui." + stepType + ".pos_to", "ponderer.ui." + stepType + ".pos_to.tooltip", PickState.TargetField.POS2);
        pos2XField = to.x();
        pos2YField = to.y();
        pos2ZField = to.z();
        var xyz = addFormXyzRow("ponderer.ui." + stepType + ".xyz", "ponderer.ui." + stepType + ".xyz.tooltip");
        xField = xyz.x();
        yField = xyz.y();
        zField = xyz.z();
        durationField = addFormNumberField("ponderer.ui.duration", "ponderer.ui.duration.tooltip.section_animation", "20", 60);
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.linkId != null) linkIdField.setValue(step.linkId);
        if (step.blockPos != null && step.blockPos.size() >= 3) {
            posXField.setValue(String.valueOf(step.blockPos.get(0)));
            posYField.setValue(String.valueOf(step.blockPos.get(1)));
            posZField.setValue(String.valueOf(step.blockPos.get(2)));
        }
        if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
            pos2XField.setValue(String.valueOf(step.blockPos2.get(0)));
            pos2YField.setValue(String.valueOf(step.blockPos2.get(1)));
            pos2ZField.setValue(String.valueOf(step.blockPos2.get(2)));
        }
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
        m.put("posX", posXField.getValue());
        m.put("posY", posYField.getValue());
        m.put("posZ", posZField.getValue());
        m.put("pos2X", pos2XField.getValue());
        m.put("pos2Y", pos2YField.getValue());
        m.put("pos2Z", pos2ZField.getValue());
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
        if (snapshot.containsKey("posX")) posXField.setValue(snapshot.get("posX"));
        if (snapshot.containsKey("posY")) posYField.setValue(snapshot.get("posY"));
        if (snapshot.containsKey("posZ")) posZField.setValue(snapshot.get("posZ"));
        if (snapshot.containsKey("pos2X")) pos2XField.setValue(snapshot.get("pos2X"));
        if (snapshot.containsKey("pos2Y")) pos2YField.setValue(snapshot.get("pos2Y"));
        if (snapshot.containsKey("pos2Z")) pos2ZField.setValue(snapshot.get("pos2Z"));
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

        Integer px = parseOptionalInt(posXField.getValue(), "From X");
        Integer py = parseOptionalInt(posYField.getValue(), "From Y");
        Integer pz = parseOptionalInt(posZField.getValue(), "From Z");
        boolean hasPos1 = px != null || py != null || pz != null;
        if (hasPos1 && (px == null || py == null || pz == null)) {
            errorMessage = UIText.of("ponderer.ui." + stepType + ".error.partial_from");
            return null;
        }

        String pos2X = pos2XField.getValue().trim();
        String pos2Y = pos2YField.getValue().trim();
        String pos2Z = pos2ZField.getValue().trim();
        boolean hasPos2 = !pos2X.isEmpty() || !pos2Y.isEmpty() || !pos2Z.isEmpty();
        Integer px2 = null;
        Integer py2 = null;
        Integer pz2 = null;
        if (hasPos2) {
            if (pos2X.isEmpty() || pos2Y.isEmpty() || pos2Z.isEmpty()) {
                errorMessage = UIText.of("ponderer.ui." + stepType + ".error.partial_to");
                return null;
            }
            px2 = parseInt(pos2X, "To X");
            py2 = parseInt(pos2Y, "To Y");
            pz2 = parseInt(pos2Z, "To Z");
            if (px2 == null || py2 == null || pz2 == null) return null;
        }

        if (hasPos2 && !hasPos1) {
            errorMessage = UIText.of("ponderer.ui." + stepType + ".error.partial_from");
            return null;
        }

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = stepType;
        String link = linkIdField.getValue().trim();
        if (!link.isEmpty()) s.linkId = link;
        s.duration = Math.max(0, duration);
        if (hasPos1) s.blockPos = List.of(px, py, pz);
        if (hasPos2) s.blockPos2 = List.of(px2, py2, pz2);

        if (rotationMode) {
            s.rotX = x.floatValue();
            s.rotY = y.floatValue();
            s.rotZ = z.floatValue();
        } else {
            s.offset = List.of(x, y, z);
        }

        return s;
    }

    @Nullable
    private Integer parseOptionalInt(String raw, String label) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) return null;
        return parseInt(trimmed, label);
    }
}
