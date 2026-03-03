package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.LocalizedText;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.HashMap;
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

    private HintableTextFieldWidget textField;
    private HintableTextFieldWidget pointXField, pointYField, pointZField;
    private HintableTextFieldWidget durationField;
    private int colorIndex = 0;
    private BoxWidget colorButton;
    private boolean placeNearTarget = false;
    private BoxWidget placeToggle;
    private PonderButton pickBtnPoint;

    /** The language currently being edited; defaults to MC's current language. */
    private String editingLang;
    /** A working copy of the LocalizedText being built up across language switches. */
    private LocalizedText workingText;
    private BoxWidget langButton;

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

    @Override protected int getFormRowCount() { return 5; }
    @Override protected String getHeaderTitle() { return UIText.of("ponderer.ui.text"); }

    @Override
    protected void buildForm() {
        beginForm();
        // Row 1: text field + lang toggle button
        var langField = addFormTextFieldWithLang("ponderer.ui.text", "ponderer.ui.text.tooltip",
                UIText.of("ponderer.ui.text.hint"), 104, () -> editingLang, this::toggleLang);
        textField = langField.field();
        langButton = langField.langBtn();
        // Row 2: point XYZ + pick
        var pos = addFormXyzRow("ponderer.ui.point", "ponderer.ui.point.tooltip", PickState.TargetField.POINT, true);
        pointXField = pos.x(); pointYField = pos.y(); pointZField = pos.z(); pickBtnPoint = pos.pickBtn();
        // Row 3: duration
        durationField = addFormNumberField("ponderer.ui.duration", "ponderer.ui.duration.tooltip.text", "60", 50, "ponderer.ui.ticks");
        // Row 4: color cycle button
        colorButton = addFormCycleButton("ponderer.ui.color", "ponderer.ui.color.tooltip", 100,
                () -> colorIndex = (colorIndex + 1) % COLORS.length,
                () -> colorIndex == 0 ? UIText.of("ponderer.ui.none") : colorLabel(COLORS[colorIndex]),
                () -> colorIndex == 0 ? 0xFFFFFF : getPaletteColor(COLORS[colorIndex]));
        // Row 5: place near toggle
        placeToggle = addFormToggle("ponderer.ui.place_near", "ponderer.ui.place_near.tooltip",
                () -> placeNearTarget, () -> placeNearTarget = !placeNearTarget);
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
    protected String getStepType() { return "text"; }

    @Override
    protected Map<String, String> snapshotForm() {
        Map<String, String> m = new HashMap<>();
        m.put("text", textField.getValue());
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
        if (snapshot.containsKey("text")) textField.setValue(snapshot.get("text"));
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
        String text = textField.getValue();
        if (text.isEmpty()) { errorMessage = UIText.of("ponderer.ui.text.error.required"); return null; }

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "text";
        // Save current field text into the working copy for the editing language
        workingText.setForLang(editingLang, text);
        s.text = workingText;
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
}
