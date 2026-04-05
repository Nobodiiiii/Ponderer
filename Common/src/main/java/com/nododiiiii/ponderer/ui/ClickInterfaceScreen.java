package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class ClickInterfaceScreen extends AbstractStepEditorScreen {

    private static final String CLICK_ACTION_LEFT = "left";
    private static final String CLICK_ACTION_RIGHT = "right";

    private final StepXyzFieldHandle pointField = new StepXyzFieldHandle("point");
    private String clickAction = CLICK_ACTION_LEFT;

    public ClickInterfaceScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.click_interface"), scene, sceneIndex, parent);
    }

    public ClickInterfaceScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.click_interface"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override
    protected String getHeaderTitle() {
        return UIText.of("ponderer.ui.click_interface");
    }

    @Override
    protected void collectStepEntries(List<com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.xyz(
            pointField,
            "ponderer.ui.click_interface.point",
            "ponderer.ui.click_interface.point.tooltip",
            UIText.of("ponderer.ui.click_interface.point.hint_x"),
            UIText.of("ponderer.ui.click_interface.point.hint_y"),
            UIText.of("ponderer.ui.click_interface.point.hint_z"),
            FieldDecorators.pointPick(PickState.TargetField.POINT, false)));
        entries.add(FieldSpecs.choice(
            "ponderer.ui.click_interface.action",
            "ponderer.ui.click_interface.action.tooltip",
            70,
            this::cycleClickAction,
            this::clickActionLabel));
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);

        if (step.pos != null && step.pos.size() >= 2) {
            pointField.xHandle().setValue(formatCoord(step.pos.get(0)));
            pointField.yHandle().setValue(formatCoord(step.pos.get(1)));
            pointField.zHandle().setValue(step.pos.size() >= 3 ? formatCoord(step.pos.get(2)) : "0.000");
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
    protected void appendCustomSnapshot(Map<String, String> snapshot) {
        snapshot.put("clickAction", clickAction);
    }

    @Override
    protected void restoreCustomSnapshot(Map<String, String> snapshot) {
        if (snapshot.containsKey("clickAction") && CLICK_ACTION_RIGHT.equalsIgnoreCase(snapshot.get("clickAction"))) {
            clickAction = CLICK_ACTION_RIGHT;
        } else {
            clickAction = CLICK_ACTION_LEFT;
        }
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        clearStatusMessages();

        Double x = parseDouble(pointField.x());
        Double y = parseDouble(pointField.y());
        Double z = parseDouble(pointField.z());
        if (x == null || y == null) {
            setErrorMessage(UIText.of("ponderer.ui.click_interface.error.invalid_point"));
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
