package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.LocalizedText;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Editor for "text" step.
 * Fields: text, point XYZ, duration, color, placeNearTarget, attachKeyFrame
 */
public class TextStepScreen extends AbstractStepEditorScreen {

    private static final String[] COLORS = {
        "", "white", "black", "red", "green", "blue",
        "input", "output", "slow", "medium", "fast"
    };

    private final StepTextFieldHandle textField = new StepTextFieldHandle("text");
    private final StepXyzFieldHandle pointField = new StepXyzFieldHandle("point");
    private final StepTextFieldHandle durationField = new StepTextFieldHandle("duration");
    private int colorIndex = 0;
    private boolean placeNearTarget = false;

    /** The language currently being edited; defaults to MC's current language. */
    private String editingLang;
    /** A working copy of the LocalizedText being built up across language switches. */
    private LocalizedText workingText;

    public TextStepScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.text"), scene, sceneIndex, parent);
        this.editingLang = getCurrentLang();
        this.workingText = LocalizedText.of("");
    }

    public TextStepScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                          int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.text"), scene, sceneIndex, parent, editIndex, step);
        this.editingLang = getCurrentLang();
        // Deep-copy the existing text so edits don't mutate the original until confirm
        this.workingText = step.text != null ? step.text : LocalizedText.of("");
    }

    @Override protected String getHeaderTitle() { return UIText.of("ponderer.ui.text"); }

    @Override
    protected void collectStepEntries(List<com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.localizedText(
            textField,
            "ponderer.ui.text",
            "ponderer.ui.text.tooltip",
            UIText.of("ponderer.ui.text.hint"),
            104,
            () -> editingLang,
            this::toggleLang));
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
        if (step.text != null) {
            workingText = step.text;
            String val = workingText.getExact(editingLang);
            textField.setValue(val != null ? val : workingText.resolve());
        }
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
    protected String getStepType() { return "text"; }

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
        String text = textField.getValue();
        if (text.isEmpty()) { setErrorMessage(UIText.of("ponderer.ui.text.error.required")); return null; }

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "text";
        // Save current field text into the working copy for the editing language
        workingText.setForLang(editingLang, text);
        s.text = workingText;
        String rawX = pointField.x();
        String rawY = pointField.y();
        String rawZ = pointField.z();
        boolean allBlank = isBlank(rawX) && isBlank(rawY) && isBlank(rawZ);
        if (!allBlank) {
            Double px = parseDouble(rawX, "X");
            Double py = parseDouble(rawY, "Y");
            Double pz = parseDouble(rawZ, "Z");
            if (px == null || py == null || pz == null) return null;
            s.point = List.of(px, py, pz);
        }
        s.duration = parseIntOr(durationField.getValue(), 60);
        if (colorIndex > 0) s.color = COLORS[colorIndex];
        if (placeNearTarget) s.placeNearTarget = true;
        return s;
    }

    /** Toggle between editing the current MC language and en_us. */
    private void toggleLang() {
        // Save current text into workingText for the current editingLang
        String currentText = textField.getValue();
        if (!currentText.isEmpty()) {
            workingText.setForLang(editingLang, currentText);
        }

        // Switch language
        String mcLang = getCurrentLang();
        if (editingLang.equals("en_us") && !"en_us".equals(mcLang)) {
            editingLang = mcLang;
        } else {
            editingLang = "en_us";
        }

        // Load text for the new editingLang
        String val = workingText.getExact(editingLang);
        textField.setValue(val != null ? val : "");
    }

    private static String getCurrentLang() {
        try {
            return Minecraft.getInstance().getLanguageManager().getSelected();
        } catch (Exception e) {
            return "en_us";
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
