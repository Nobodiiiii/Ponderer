package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;

public class SectionTransformScreen extends AbstractStepEditorScreen {

    private final String stepType;
    private final boolean rotationMode;

    private final StepTextFieldHandle linkIdField = new StepTextFieldHandle("linkId");
    private final StepXyzFieldHandle posField = new StepXyzFieldHandle("pos");
    private final StepXyzFieldHandle pos2Field = new StepXyzFieldHandle("pos2");
    private final StepXyzFieldHandle xyzField = new StepXyzFieldHandle("x", "y", "z");
    private final StepTextFieldHandle durationField = new StepTextFieldHandle("duration");

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
    protected String getHeaderTitle() { return UIText.of("ponderer.ui." + stepType); }

    @Override
    protected void collectStepEntries(List<com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry> entries) {
        entries.add(StepEditorEntries.text(
            linkIdField,
            "ponderer.ui." + stepType + ".link",
            "ponderer.ui." + stepType + ".link.tooltip",
            "",
            140));
        entries.add(StepEditorEntries.xyz(
            posField,
            "ponderer.ui." + stepType + ".pos_from",
            "ponderer.ui." + stepType + ".pos_from.tooltip",
            PickState.TargetField.POS1));
        entries.add(StepEditorEntries.xyz(
            pos2Field,
            "ponderer.ui." + stepType + ".pos_to",
            "ponderer.ui." + stepType + ".pos_to.tooltip",
            PickState.TargetField.POS2));
        entries.add(StepEditorEntries.xyz(
            xyzField,
            "ponderer.ui." + stepType + ".xyz",
            "ponderer.ui." + stepType + ".xyz.tooltip"));
        entries.add(StepEditorEntries.number(
            durationField,
            "ponderer.ui.duration",
            "ponderer.ui.duration.tooltip.section_animation",
            "20",
            60,
            null));
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.linkId != null) linkIdField.setValue(step.linkId);
        if (step.blockPos != null && step.blockPos.size() >= 3) {
            posField.setValue(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
        }
        if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
            pos2Field.setValue(step.blockPos2.get(0), step.blockPos2.get(1), step.blockPos2.get(2));
        }
        if (rotationMode) {
            if (step.rotX != null) xyzField.xHandle().setValue(String.valueOf(step.rotX));
            if (step.rotY != null) xyzField.yHandle().setValue(String.valueOf(step.rotY));
            if (step.rotZ != null) xyzField.zHandle().setValue(String.valueOf(step.rotZ));
        } else if (step.offset != null && step.offset.size() >= 3) {
            xyzField.setValue(step.offset.get(0), step.offset.get(1), step.offset.get(2));
        }
        if (step.duration != null) durationField.setValue(String.valueOf(step.duration));
    }

    @Override
    protected String getStepType() { return stepType; }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;

        Double x = parseDouble(xyzField.x(), "X");
        Double y = parseDouble(xyzField.y(), "Y");
        Double z = parseDouble(xyzField.z(), "Z");
        if (x == null || y == null || z == null) return null;

        int duration = parseIntOr(durationField.getValue(), 20);

        Integer px = parseOptionalInt(posField.x(), "From X");
        Integer py = parseOptionalInt(posField.y(), "From Y");
        Integer pz = parseOptionalInt(posField.z(), "From Z");
        boolean hasPos1 = px != null || py != null || pz != null;
        if (hasPos1 && (px == null || py == null || pz == null)) {
            errorMessage = UIText.of("ponderer.ui." + stepType + ".error.partial_from");
            return null;
        }

        String pos2X = pos2Field.x().trim();
        String pos2Y = pos2Field.y().trim();
        String pos2Z = pos2Field.z().trim();
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
