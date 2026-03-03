package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/** Editor for "idle" step - duration in ticks. */
public class IdleScreen extends AbstractStepEditorScreen {

    private HintableTextFieldWidget durationField;

    public IdleScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.idle"), scene, sceneIndex, parent);
    }

    public IdleScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                      int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.idle"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override protected int getFormRowCount() { return 1; }
    @Override protected String getHeaderTitle() { return UIText.of("ponderer.ui.idle"); }

    @Override
    protected void buildForm() {
        beginForm();
        durationField = addFormNumberField("ponderer.ui.duration", "ponderer.ui.duration.tooltip.idle", "20", 60, "ponderer.ui.ticks");
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.duration != null) durationField.setValue(String.valueOf(step.duration));
    }

    @Override
    protected String getStepType() { return "idle"; }

    @Override
    protected Map<String, String> snapshotForm() {
        Map<String, String> m = new HashMap<>();
        m.put("duration", durationField.getValue());
        return m;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        if (snapshot.containsKey("duration")) durationField.setValue(snapshot.get("duration"));
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;
        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "idle";
        s.duration = parseIntOr(durationField.getValue(), 20);
        if (s.duration < 0) { errorMessage = UIText.of("ponderer.ui.idle.error.duration"); return null; }
        return s;
    }
}
