package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Editor for "encapsulate_bounds" step.
 * Fields: bounds X, Y, Z (3 integers defining the bounding box size).
 */
public class EncapsulateBoundsScreen extends AbstractStepEditorScreen {

    private HintableTextFieldWidget boundsXField, boundsYField, boundsZField;

    public EncapsulateBoundsScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.encapsulate_bounds"), scene, sceneIndex, parent);
    }

    public EncapsulateBoundsScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                   int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.encapsulate_bounds"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override protected int getFormRowCount() { return 1; }
    @Override protected String getHeaderTitle() { return UIText.of("ponderer.ui.encapsulate_bounds"); }

    @Override
    protected void buildForm() {
        beginForm();
        var xyz = addFormXyzRow("ponderer.ui.encapsulate_bounds.bounds", "ponderer.ui.encapsulate_bounds.bounds.tooltip");
        boundsXField = xyz.x();
        boundsYField = xyz.y();
        boundsZField = xyz.z();
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.bounds != null && step.bounds.size() >= 3) {
            boundsXField.setValue(String.valueOf(step.bounds.get(0)));
            boundsYField.setValue(String.valueOf(step.bounds.get(1)));
            boundsZField.setValue(String.valueOf(step.bounds.get(2)));
        }
    }

    @Override
    protected String getStepType() { return "encapsulate_bounds"; }

    @Override
    protected Map<String, String> snapshotForm() {
        Map<String, String> m = new HashMap<>();
        m.put("boundsX", boundsXField.getValue());
        m.put("boundsY", boundsYField.getValue());
        m.put("boundsZ", boundsZField.getValue());
        return m;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        if (snapshot.containsKey("boundsX")) boundsXField.setValue(snapshot.get("boundsX"));
        if (snapshot.containsKey("boundsY")) boundsYField.setValue(snapshot.get("boundsY"));
        if (snapshot.containsKey("boundsZ")) boundsZField.setValue(snapshot.get("boundsZ"));
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;
        Integer bx = parseInt(boundsXField.getValue(), "X");
        Integer by = parseInt(boundsYField.getValue(), "Y");
        Integer bz = parseInt(boundsZField.getValue(), "Z");
        if (bx == null || by == null || bz == null) return null;

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "encapsulate_bounds";
        s.bounds = List.of(bx, by, bz);
        return s;
    }
}
