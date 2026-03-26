package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClickInterfaceScreen extends AbstractStepEditorScreen {

    private static final String CLICK_ACTION_LEFT = "left";
    private static final String CLICK_ACTION_RIGHT = "right";

    private HintableTextFieldWidget pointXField;
    private HintableTextFieldWidget pointYField;
    private HintableTextFieldWidget pointZField;
    private String clickAction = CLICK_ACTION_LEFT;

    public ClickInterfaceScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.click_interface"), scene, sceneIndex, parent);
    }

    public ClickInterfaceScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.click_interface"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override
    protected int getFormRowCount() {
        return 2;
    }

    @Override
    protected String getHeaderTitle() {
        return UIText.of("ponderer.ui.click_interface");
    }

    @Override
    protected void buildForm() {
        beginForm();

        var point = addFormXyzRow(
            "ponderer.ui.click_interface.point",
            "ponderer.ui.click_interface.point.tooltip",
            PickState.TargetField.POINT);
        pointXField = point.x();
        pointYField = point.y();
        pointZField = point.z();
        pointXField.setHint(UIText.of("ponderer.ui.click_interface.point.hint_x"));
        pointYField.setHint(UIText.of("ponderer.ui.click_interface.point.hint_y"));
        pointZField.setHint(UIText.of("ponderer.ui.click_interface.point.hint_z"));

        addFormCycleButton(
            "ponderer.ui.click_interface.action",
            "ponderer.ui.click_interface.action.tooltip",
            70,
            this::cycleClickAction,
            this::clickActionLabel
        );
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);

        if (step.pos != null && step.pos.size() >= 2) {
            pointXField.setValue(formatCoord(step.pos.get(0)));
            pointYField.setValue(formatCoord(step.pos.get(1)));
            pointZField.setValue(step.pos.size() >= 3 ? formatCoord(step.pos.get(2)) : "0.000");
        }

        if (CLICK_ACTION_RIGHT.equalsIgnoreCase(step.action)) {
            clickAction = CLICK_ACTION_RIGHT;
        } else {
            clickAction = CLICK_ACTION_LEFT;
        }
    }

    @Override
    protected String getStepType() {
        return "click_interface";
    }

    @Override
    protected Map<String, String> snapshotForm() {
        Map<String, String> snapshot = new HashMap<>();
        snapshot.put("pointX", pointXField.getValue());
        snapshot.put("pointY", pointYField.getValue());
        snapshot.put("pointZ", pointZField.getValue());
        snapshot.put("clickAction", clickAction);
        return snapshot;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        if (snapshot.containsKey("pointX")) {
            pointXField.setValue(snapshot.get("pointX"));
        }
        if (snapshot.containsKey("pointY")) {
            pointYField.setValue(snapshot.get("pointY"));
        }
        if (snapshot.containsKey("pointZ")) {
            pointZField.setValue(snapshot.get("pointZ"));
        }
        if (snapshot.containsKey("clickAction") && CLICK_ACTION_RIGHT.equalsIgnoreCase(snapshot.get("clickAction"))) {
            clickAction = CLICK_ACTION_RIGHT;
        } else {
            clickAction = CLICK_ACTION_LEFT;
        }
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;

        Double x = parseDouble(pointXField.getValue());
        Double y = parseDouble(pointYField.getValue());
        Double z = parseDouble(pointZField.getValue());
        if (x == null || y == null) {
            errorMessage = UIText.of("ponderer.ui.click_interface.error.invalid_point");
            return null;
        }

        DslScene.DslStep step = new DslScene.DslStep();
        step.type = "click_interface";
        step.action = clickAction;
        step.pos = List.of(x, y, z == null ? 0.0 : z);
        step.duration = null;
        return step;
    }

    private void cycleClickAction() {
        clickAction = CLICK_ACTION_LEFT.equals(clickAction) ? CLICK_ACTION_RIGHT : CLICK_ACTION_LEFT;
    }

    private String clickActionLabel() {
        return CLICK_ACTION_LEFT.equals(clickAction)
            ? UIText.of("ponderer.ui.click_interface.action.left")
            : UIText.of("ponderer.ui.click_interface.action.right");
    }

    @Nullable
    private static Double parseDouble(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String formatCoord(double v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }
}
