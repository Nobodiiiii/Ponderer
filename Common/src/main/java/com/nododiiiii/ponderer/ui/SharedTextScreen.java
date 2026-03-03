package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Editor for "shared_text" step.
 * Fields: key, point XYZ, duration, color, placeNearTarget, attachKeyFrame
 */
public class SharedTextScreen extends AbstractStepEditorScreen {

    private static final String[] COLORS = {
        "", "white", "black", "red", "green", "blue",
        "input", "output", "slow", "medium", "fast"
    };

    private HintableTextFieldWidget keyField;
    private HintableTextFieldWidget pointXField, pointYField, pointZField;
    private HintableTextFieldWidget durationField;
    private int colorIndex = 0;
    private BoxWidget colorButton;
    private boolean placeNearTarget = false;
    private BoxWidget placeToggle;
    private PonderButton pickBtnPoint;

    public SharedTextScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.shared_text"), scene, sceneIndex, parent);
    }

    public SharedTextScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                            int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.shared_text"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override protected int getFormRowCount() { return 5; }
    @Override protected String getHeaderTitle() { return UIText.of("ponderer.ui.shared_text"); }

    @Override
    protected void buildForm() {
        beginForm();
        keyField = addFormTextField("ponderer.ui.shared_text.key", "ponderer.ui.shared_text.key.tooltip", UIText.of("ponderer.ui.shared_text.key.hint"), 140);
        var point = addFormXyzRow("ponderer.ui.point", "ponderer.ui.point.tooltip", PickState.TargetField.POINT, true);
        pointXField = point.x(); pointYField = point.y(); pointZField = point.z(); pickBtnPoint = point.pickBtn();
        durationField = addFormNumberField("ponderer.ui.duration", "ponderer.ui.duration.tooltip.text", "60", 50, "ponderer.ui.ticks");
        colorButton = addFormCycleButton("ponderer.ui.color", "ponderer.ui.color.tooltip", 100,
                () -> colorIndex = (colorIndex + 1) % COLORS.length,
                () -> colorIndex == 0 ? UIText.of("ponderer.ui.none") : colorLabel(COLORS[colorIndex]),
                () -> colorIndex == 0 ? 0xFFFFFF : getPaletteColor(COLORS[colorIndex]));
        placeToggle = addFormToggle("ponderer.ui.place_near", "ponderer.ui.place_near.tooltip",
                () -> placeNearTarget, () -> placeNearTarget = !placeNearTarget);
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.key != null) keyField.setValue(step.key);
        if (step.point != null && step.point.size() >= 3) {
            pointXField.setValue(String.valueOf(step.point.get(0)));
            pointYField.setValue(String.valueOf(step.point.get(1)));
            pointZField.setValue(String.valueOf(step.point.get(2)));
        }
        if (step.duration != null) durationField.setValue(String.valueOf(step.duration));
        if (step.color != null) {
            for (int i = 0; i < COLORS.length; i++) {
                if (COLORS[i].equalsIgnoreCase(step.color)) { colorIndex = i; break; }
            }
        }
        placeNearTarget = Boolean.TRUE.equals(step.placeNearTarget);
    }

    private String colorLabel(String value) {
        String key = "ponderer.ui.color.option." + value;
        String translated = UIText.of(key);
        return key.equals(translated) ? value : translated;
    }

    @Override
    protected String getStepType() { return "shared_text"; }

    @Override
    protected Map<String, String> snapshotForm() {
        Map<String, String> m = new HashMap<>();
        m.put("key", keyField.getValue());
        m.put("pointX", pointXField.getValue());
        m.put("pointY", pointYField.getValue());
        m.put("pointZ", pointZField.getValue());
        m.put("duration", durationField.getValue());
        m.put("colorIndex", String.valueOf(colorIndex));
        m.put("placeNearTarget", String.valueOf(placeNearTarget));
        return m;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        if (snapshot.containsKey("key")) keyField.setValue(snapshot.get("key"));
        if (snapshot.containsKey("pointX")) pointXField.setValue(snapshot.get("pointX"));
        if (snapshot.containsKey("pointY")) pointYField.setValue(snapshot.get("pointY"));
        if (snapshot.containsKey("pointZ")) pointZField.setValue(snapshot.get("pointZ"));
        if (snapshot.containsKey("duration")) durationField.setValue(snapshot.get("duration"));
        if (snapshot.containsKey("colorIndex")) {
            try { colorIndex = Integer.parseInt(snapshot.get("colorIndex")); } catch (NumberFormatException ignored) {}
        }
        if (snapshot.containsKey("placeNearTarget")) placeNearTarget = Boolean.parseBoolean(snapshot.get("placeNearTarget"));
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;
        String key = keyField.getValue().trim();
        if (key.isEmpty()) { errorMessage = UIText.of("ponderer.ui.shared_text.error.required"); return null; }

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "shared_text";
        s.key = key;
        Double px = parseDouble(pointXField.getValue(), "X");
        Double py = parseDouble(pointYField.getValue(), "Y");
        Double pz = parseDouble(pointZField.getValue(), "Z");
        if (px == null || py == null || pz == null) return null;
        s.point = List.of(px, py, pz);
        s.duration = parseIntOr(durationField.getValue(), 60);
        if (colorIndex > 0) s.color = COLORS[colorIndex];
        if (placeNearTarget) s.placeNearTarget = true;
        return s;
    }
}
