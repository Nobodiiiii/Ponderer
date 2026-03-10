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

public class HighlightSectionScreen extends AbstractStepEditorScreen {

    private static final String[] COLORS = {
        "blue", "white", "black", "red", "green",
        "input", "output", "slow", "medium", "fast"
    };

    private HintableTextFieldWidget pos1XField, pos1YField, pos1ZField;
    private HintableTextFieldWidget pos2XField, pos2YField, pos2ZField;
    private HintableTextFieldWidget durationField;
    private int colorIndex = 0;
    private BoxWidget colorButton;
    private PonderButton pickBtn1, pickBtn2;

    public HighlightSectionScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.highlight_section.add"), scene, sceneIndex, parent);
    }

    public HighlightSectionScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                   int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.highlight_section.edit"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override
    protected int getFormRowCount() { return 4; }

    @Override
    protected String getHeaderTitle() { return UIText.of("ponderer.ui.highlight_section"); }

    @Override
    protected void buildForm() {
        beginForm();
        var pos1 = addFormXyzRow("ponderer.ui.highlight_section.pos_from", "ponderer.ui.highlight_section.pos_from.tooltip", PickState.TargetField.POS1);
        pos1XField = pos1.x(); pos1YField = pos1.y(); pos1ZField = pos1.z(); pickBtn1 = pos1.pickBtn();
        var pos2 = addFormXyzRow("ponderer.ui.highlight_section.pos_to", "ponderer.ui.highlight_section.pos_to.tooltip", PickState.TargetField.POS2);
        pos2XField = pos2.x(); pos2YField = pos2.y(); pos2ZField = pos2.z(); pickBtn2 = pos2.pickBtn();
        durationField = addFormNumberField("ponderer.ui.duration", "ponderer.ui.highlight_section.duration.tooltip", "40", 50, "ponderer.ui.ticks");
        colorButton = addFormCycleButton("ponderer.ui.color", "ponderer.ui.color.tooltip", 100,
                () -> colorIndex = (colorIndex + 1) % COLORS.length,
                () -> colorLabel(COLORS[colorIndex]),
                () -> getPaletteColor(COLORS[colorIndex]));
    }

    private String colorLabel(String value) {
        String key = "ponderer.ui.color.option." + value;
        String translated = UIText.of(key);
        return key.equals(translated) ? value : translated;
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.blockPos != null && step.blockPos.size() >= 3) {
            pos1XField.setValue(String.valueOf(step.blockPos.get(0)));
            pos1YField.setValue(String.valueOf(step.blockPos.get(1)));
            pos1ZField.setValue(String.valueOf(step.blockPos.get(2)));
        }
        if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
            pos2XField.setValue(String.valueOf(step.blockPos2.get(0)));
            pos2YField.setValue(String.valueOf(step.blockPos2.get(1)));
            pos2ZField.setValue(String.valueOf(step.blockPos2.get(2)));
        }
        if (step.duration != null) durationField.setValue(String.valueOf(step.duration));
        if (step.color != null) {
            for (int i = 0; i < COLORS.length; i++) {
                if (COLORS[i].equalsIgnoreCase(step.color)) { colorIndex = i; break; }
            }
        }
    }

    @Override
    protected String getStepType() { return "highlight_section"; }

    @Override
    protected Map<String, String> snapshotForm() {
        Map<String, String> m = new HashMap<>();
        m.put("posX", pos1XField.getValue());
        m.put("posY", pos1YField.getValue());
        m.put("posZ", pos1ZField.getValue());
        m.put("pos2X", pos2XField.getValue());
        m.put("pos2Y", pos2YField.getValue());
        m.put("pos2Z", pos2ZField.getValue());
        m.put("duration", durationField.getValue());
        m.put("colorIndex", String.valueOf(colorIndex));
        return m;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        if (snapshot.containsKey("posX")) pos1XField.setValue(snapshot.get("posX"));
        if (snapshot.containsKey("posY")) pos1YField.setValue(snapshot.get("posY"));
        if (snapshot.containsKey("posZ")) pos1ZField.setValue(snapshot.get("posZ"));
        if (snapshot.containsKey("pos2X")) pos2XField.setValue(snapshot.get("pos2X"));
        if (snapshot.containsKey("pos2Y")) pos2YField.setValue(snapshot.get("pos2Y"));
        if (snapshot.containsKey("pos2Z")) pos2ZField.setValue(snapshot.get("pos2Z"));
        if (snapshot.containsKey("duration")) durationField.setValue(snapshot.get("duration"));
        if (snapshot.containsKey("colorIndex")) {
            try { colorIndex = Integer.parseInt(snapshot.get("colorIndex")); } catch (NumberFormatException ignored) {}
        }
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;

        Integer p1x = parseInt(pos1XField.getValue(), "From X");
        Integer p1y = parseInt(pos1YField.getValue(), "From Y");
        Integer p1z = parseInt(pos1ZField.getValue(), "From Z");
        if (p1x == null || p1y == null || p1z == null) return null;

        Integer p2x = parseOptionalInt(pos2XField.getValue(), "To X");
        Integer p2y = parseOptionalInt(pos2YField.getValue(), "To Y");
        Integer p2z = parseOptionalInt(pos2ZField.getValue(), "To Z");
        boolean hasPos2 = p2x != null || p2y != null || p2z != null;
        if (hasPos2 && (p2x == null || p2y == null || p2z == null)) {
            errorMessage = UIText.of("ponderer.ui.highlight_section.error.partial_to");
            return null;
        }

        int duration = Math.max(1, parseIntOr(durationField.getValue(), 40));

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "highlight_section";
        s.blockPos = List.of(p1x, p1y, p1z);
        if (hasPos2) s.blockPos2 = List.of(p2x, p2y, p2z);
        s.duration = duration;
        s.color = COLORS[colorIndex];
        return s;
    }

    @Nullable
    private Integer parseOptionalInt(String raw, String label) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) return null;
        return parseInt(trimmed, label);
    }
}
