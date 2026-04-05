package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class HighlightSectionScreen extends AbstractStepEditorScreen {

    private static final String[] COLORS = {
        "blue", "white", "black", "red", "green",
        "input", "output", "slow", "medium", "fast"
    };

    private final StepXyzFieldHandle pos1Field = new StepXyzFieldHandle("pos");
    private final StepXyzFieldHandle pos2Field = new StepXyzFieldHandle("pos2");
    private final StepTextFieldHandle durationField = new StepTextFieldHandle("duration");
    private int colorIndex = 0;

    public HighlightSectionScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.highlight_section.add"), scene, sceneIndex, parent);
    }

    public HighlightSectionScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                   int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.highlight_section.edit"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override
    protected String getHeaderTitle() { return UIText.of("ponderer.ui.highlight_section"); }

    @Override
    protected void collectStepEntries(List<com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.xyz(
            pos1Field,
            "ponderer.ui.highlight_section.pos_from",
            "ponderer.ui.highlight_section.pos_from.tooltip",
            PickState.TargetField.POS1));
        entries.add(FieldSpecs.xyz(
            pos2Field,
            "ponderer.ui.highlight_section.pos_to",
            "ponderer.ui.highlight_section.pos_to.tooltip",
            PickState.TargetField.POS2));
        entries.add(FieldSpecs.number(
            durationField,
            "ponderer.ui.duration",
            "ponderer.ui.highlight_section.duration.tooltip",
            "40",
            50,
            "ponderer.ui.ticks"));
        entries.add(FieldSpecs.choice(
            "ponderer.ui.color",
            "ponderer.ui.color.tooltip",
            100,
            () -> colorIndex = (colorIndex + 1) % COLORS.length,
            () -> colorLabel(COLORS[colorIndex]),
            () -> getPaletteColor(COLORS[colorIndex])));
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
            pos1Field.setValue(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
        }
        if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
            pos2Field.setValue(step.blockPos2.get(0), step.blockPos2.get(1), step.blockPos2.get(2));
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
    protected void appendCustomSnapshot(Map<String, String> snapshot) {
        snapshot.put("colorIndex", String.valueOf(colorIndex));
    }

    @Override
    protected void restoreCustomSnapshot(Map<String, String> snapshot) {
        if (snapshot.containsKey("colorIndex")) {
            try { colorIndex = Integer.parseInt(snapshot.get("colorIndex")); } catch (NumberFormatException ignored) {}
        }
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;

        Integer p1x = parseInt(pos1Field.x(), "From X");
        Integer p1y = parseInt(pos1Field.y(), "From Y");
        Integer p1z = parseInt(pos1Field.z(), "From Z");
        if (p1x == null || p1y == null || p1z == null) return null;

        Integer p2x = parseOptionalInt(pos2Field.x(), "To X");
        Integer p2y = parseOptionalInt(pos2Field.y(), "To Y");
        Integer p2z = parseOptionalInt(pos2Field.z(), "To Z");
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
