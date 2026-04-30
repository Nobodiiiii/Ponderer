package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
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

    private final StepTextFieldHandle keyField = new StepTextFieldHandle("key");
    private final StepXyzFieldHandle pointField = new StepXyzFieldHandle("point");
    private final StepTextFieldHandle durationField = new StepTextFieldHandle("duration");
    private int colorIndex = 0;
    private boolean placeNearTarget = false;

    public SharedTextScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.shared_text"), scene, sceneIndex, parent);
    }

    public SharedTextScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                            int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.shared_text"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override protected String getHeaderTitle() { return UIText.of("ponderer.ui.shared_text"); }

    @Override
    protected void collectStepEntries(List<com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.text(
            keyField,
            "ponderer.ui.shared_text.key",
            "ponderer.ui.shared_text.key.tooltip",
            UIText.of("ponderer.ui.shared_text.key.hint"),
            140));
        entries.add(FieldSpecs.xyz(
            pointField,
            "ponderer.ui.point",
            "ponderer.ui.point.tooltip",
            PickState.TargetField.POINT,
            true));
        entries.add(FieldSpecs.ticksNumber(
            durationField,
            "ponderer.ui.duration",
            "ponderer.ui.duration.tooltip.text",
            "60",
            50));
        entries.add(FieldSpecs.choice(
            "ponderer.ui.color",
            "ponderer.ui.color.tooltip",
            100,
            () -> colorIndex = (colorIndex + 1) % COLORS.length,
            () -> colorIndex == 0 ? UIText.of("ponderer.ui.none") : colorLabel(COLORS[colorIndex]),
            () -> colorIndex == 0 ? 0xFFFFFF : getPaletteColor(COLORS[colorIndex])));
        entries.add(FieldSpecs.toggle(
            "ponderer.ui.place_near",
            "ponderer.ui.place_near.tooltip",
            () -> placeNearTarget,
            () -> placeNearTarget = !placeNearTarget));
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.key != null) keyField.setValue(step.key);
        if (step.point != null && step.point.size() >= 3) {
            pointField.setValue(step.point.get(0), step.point.get(1), step.point.get(2));
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
    protected void appendCustomSnapshot(Map<String, String> snapshot) {
        snapshot.put("colorIndex", String.valueOf(colorIndex));
        snapshot.put("placeNearTarget", String.valueOf(placeNearTarget));
    }

    @Override
    protected void restoreCustomSnapshot(Map<String, String> snapshot) {
        if (snapshot.containsKey("colorIndex")) {
            try { colorIndex = Integer.parseInt(snapshot.get("colorIndex")); } catch (NumberFormatException ignored) {}
        }
        if (snapshot.containsKey("placeNearTarget")) placeNearTarget = Boolean.parseBoolean(snapshot.get("placeNearTarget"));
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        clearStatusMessages();
        String key = keyField.getValue().trim();
        if (key.isEmpty()) { setErrorMessage(UIText.of("ponderer.ui.shared_text.error.required")); return null; }

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "shared_text";
        s.key = key;
        Double px = parseDouble(pointField.x(), "X");
        Double py = parseDouble(pointField.y(), "Y");
        Double pz = parseDouble(pointField.z(), "Z");
        if (px == null || py == null || pz == null) return null;
        s.point = List.of(px, py, pz);
        s.duration = parseIntOr(durationField.getValue(), 60);
        if (colorIndex > 0) s.color = COLORS[colorIndex];
        if (placeNearTarget) s.placeNearTarget = true;
        return s;
    }
}
