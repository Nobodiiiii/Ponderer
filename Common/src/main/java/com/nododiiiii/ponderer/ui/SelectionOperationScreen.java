package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SelectionOperationScreen extends AbstractStepEditorScreen {

    private static final String[] DIRECTIONS = {"down", "up", "north", "south", "west", "east"};
    private static final String[] ENTRANCE_ANIMATIONS = {"none", "simultaneous", "down", "up", "south", "north", "east", "west"};

    private final String stepType;
    private final boolean withDirection;
    private final boolean withLinkId;
    private final boolean withDuration;

    private final StepXyzFieldHandle posField = new StepXyzFieldHandle("pos");
    private final StepXyzFieldHandle pos2Field = new StepXyzFieldHandle("pos2");
    private final StepTextFieldHandle linkIdField = new StepTextFieldHandle("linkId");
    private final StepTextFieldHandle durationField = new StepTextFieldHandle("duration");
    private final StepTextFieldHandle intervalField = new StepTextFieldHandle("entranceInterval");
    private int directionIndex = 0;
    private int entranceAnimationIndex = 0;
    private boolean smartDisplay = true;

    public SelectionOperationScreen(String stepType, boolean withDirection, boolean withLinkId,
                                    DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        this(stepType, withDirection, withLinkId, false, scene, sceneIndex, parent);
    }

    public SelectionOperationScreen(String stepType, boolean withDirection, boolean withLinkId, boolean withDuration,
                                    DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui." + stepType + ".add"), scene, sceneIndex, parent);
        this.stepType = stepType;
        this.withDirection = withDirection;
        this.withLinkId = withLinkId;
        this.withDuration = withDuration;
    }

    public SelectionOperationScreen(String stepType, boolean withDirection, boolean withLinkId,
                                    DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                    int editIndex, DslScene.DslStep step) {
        this(stepType, withDirection, withLinkId, false, scene, sceneIndex, parent, editIndex, step);
    }

    public SelectionOperationScreen(String stepType, boolean withDirection, boolean withLinkId, boolean withDuration,
                                    DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                    int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui." + stepType + ".edit"), scene, sceneIndex, parent, editIndex, step);
        this.stepType = stepType;
        this.withDirection = withDirection;
        this.withLinkId = withLinkId;
        this.withDuration = withDuration;
    }

    private boolean supportsEntranceAnimation() {
        return "show_section_and_merge".equals(stepType);
    }

    @Override
    protected String getHeaderTitle() {
        return UIText.of("ponderer.ui." + stepType);
    }

    @Override
    protected void collectFormEntries(List<StepEditorEntry> entries) {
        entries.add(StepEditorEntries.xyz(
            posField,
            "ponderer.ui." + stepType + ".pos_from",
            "ponderer.ui." + stepType + ".pos_from.tooltip",
            PickState.TargetField.POS1));
        entries.add(StepEditorEntries.xyz(
            pos2Field,
            "ponderer.ui." + stepType + ".pos_to",
            "ponderer.ui." + stepType + ".pos_to.tooltip",
            PickState.TargetField.POS2));
        if (supportsEntranceAnimation()) {
            entries.add(StepEditorEntries.cycleButton(
                "ponderer.ui.entrance_animation",
                "ponderer.ui.entrance_animation.tooltip",
                140,
                () -> {
                    entranceAnimationIndex = (entranceAnimationIndex + 1) % ENTRANCE_ANIMATIONS.length;
                    updateTickFieldsEnabledState();
                },
                () -> entranceAnimationLabel(ENTRANCE_ANIMATIONS[entranceAnimationIndex])));
        }
        if (withDirection) {
            entries.add(StepEditorEntries.cycleButton(
                "ponderer.ui." + stepType + ".direction",
                "ponderer.ui." + stepType + ".direction.tooltip",
                140,
                () -> directionIndex = (directionIndex + 1) % DIRECTIONS.length,
                () -> optionLabel("ponderer.ui.show_controls.direction", DIRECTIONS[directionIndex])));
        }
        if (withLinkId) {
            entries.add(StepEditorEntries.text(
                linkIdField,
                "ponderer.ui." + stepType + ".link",
                "ponderer.ui." + stepType + ".link.tooltip",
                "",
                140));
        }
        if (withDuration) {
            entries.add(StepEditorEntries.number(
                durationField,
                "ponderer.ui.duration",
                "ponderer.ui.duration.tooltip.section_animation",
                "20",
                60,
                null));
        }
        if (supportsEntranceAnimation()) {
            entries.add(StepEditorEntries.number(
                intervalField,
                "ponderer.ui.entrance_interval",
                "ponderer.ui.entrance_interval.tooltip",
                "1",
                60,
                null));
            entries.add(StepEditorEntries.toggle(
                "ponderer.ui.smart_display",
                "ponderer.ui.smart_display.tooltip",
                () -> smartDisplay,
                () -> smartDisplay = !smartDisplay));
            updateTickFieldsEnabledState();
        }
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.blockPos != null && step.blockPos.size() >= 3) {
            posField.setValue(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
        }
        if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
            pos2Field.setValue(step.blockPos2.get(0), step.blockPos2.get(1), step.blockPos2.get(2));
        }
        if (withDirection && step.direction != null) {
            String normalized = normalizeDirection(step.direction);
            for (int i = 0; i < DIRECTIONS.length; i++) {
                if (DIRECTIONS[i].equals(normalized)) {
                    directionIndex = i;
                    break;
                }
            }
        }
        if (withLinkId && step.linkId != null) {
            linkIdField.setValue(step.linkId);
        }
        if (supportsEntranceAnimation() && step.entranceAnimation != null && !step.entranceAnimation.isBlank()) {
            String normalized = normalizeEntranceAnimation(step.entranceAnimation);
            for (int i = 0; i < ENTRANCE_ANIMATIONS.length; i++) {
                if (ENTRANCE_ANIMATIONS[i].equals(normalized)) {
                    entranceAnimationIndex = i;
                    break;
                }
            }
        }
        if (withDuration) {
            if (step.entranceDuration != null) {
                durationField.setValue(String.valueOf(step.entranceDuration));
            } else if (step.duration != null) {
                durationField.setValue(String.valueOf(step.duration));
            }
        }
        if (supportsEntranceAnimation() && step.entranceInterval != null) {
            intervalField.setValue(String.valueOf(step.entranceInterval));
        }
        if (supportsEntranceAnimation() && step.smartDisplay != null) {
            smartDisplay = step.smartDisplay;
        }
        updateTickFieldsEnabledState();
    }



    private String optionLabel(String prefix, String value) {
        String key = prefix + "." + value;
        String translated = UIText.of(key);
        return key.equals(translated) ? value : translated;
    }

    private String normalizeDirection(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "上", "向上", "up" -> "up";
            case "北", "向北", "north" -> "north";
            case "南", "向南", "south" -> "south";
            case "西", "向西", "west" -> "west";
            case "东", "向东", "east" -> "east";
            default -> "down";
        };
    }

    private String normalizeEntranceAnimation(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "从上到下", "上到下", "top_to_bottom", "top-down", "down" -> "down";
            case "从下到上", "下到上", "bottom_to_top", "bottom-up", "up" -> "up";
            case "从北到南", "北到南", "north_to_south", "north-south", "south" -> "south";
            case "从南到北", "南到北", "south_to_north", "south-north", "north" -> "north";
            case "从西到东", "西到东", "west_to_east", "west-east", "east" -> "east";
            case "从东到西", "东到西", "east_to_west", "east-west", "west" -> "west";
            case "同时", "simultaneous" -> "simultaneous";
            default -> "none";
        };
    }

    private String entranceAnimationLabel(String value) {
        return UIText.of("ponderer.ui.entrance_animation.option." + value);
    }

    private void updateTickFieldsEnabledState() {
        if (!supportsEntranceAnimation()) {
            return;
        }
        String mode = ENTRANCE_ANIMATIONS[entranceAnimationIndex];
        boolean durationEnabled = !"none".equals(mode);
        boolean intervalEnabled = !("none".equals(mode) || "simultaneous".equals(mode));

        if (durationField.widget() != null) {
            durationField.widget().active = durationEnabled;
        }
        if (intervalField.widget() != null) {
            intervalField.widget().active = intervalEnabled;
        }
    }

    @Override
    protected String getStepType() { return stepType; }

    @Override
    protected void appendCustomSnapshot(Map<String, String> m) {
        if (withDirection) m.put("direction", String.valueOf(directionIndex));
        if (supportsEntranceAnimation()) m.put("entranceAnimation", String.valueOf(entranceAnimationIndex));
        if (supportsEntranceAnimation()) m.put("smartDisplay", String.valueOf(smartDisplay));
    }

    @Override
    protected void restoreCustomSnapshot(Map<String, String> snapshot) {
        if (withDirection && snapshot.containsKey("direction")) {
            try { directionIndex = Integer.parseInt(snapshot.get("direction")); } catch (NumberFormatException ignored) {}
        }
        if (supportsEntranceAnimation() && snapshot.containsKey("entranceAnimation")) {
            try {
                entranceAnimationIndex = Integer.parseInt(snapshot.get("entranceAnimation"));
            } catch (NumberFormatException ignored) {
                entranceAnimationIndex = 0;
            }
            if (entranceAnimationIndex < 0 || entranceAnimationIndex >= ENTRANCE_ANIMATIONS.length) {
                entranceAnimationIndex = 0;
            }
        }
        if (supportsEntranceAnimation() && snapshot.containsKey("smartDisplay")) {
            smartDisplay = Boolean.parseBoolean(snapshot.get("smartDisplay"));
        }
        updateTickFieldsEnabledState();
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;

        Integer px = parseInt(posField.x(), "X");
        Integer py = parseInt(posField.y(), "Y");
        Integer pz = parseInt(posField.z(), "Z");
        if (px == null || py == null || pz == null) return null;

        String pos2X = pos2Field.x().trim();
        String pos2Y = pos2Field.y().trim();
        String pos2Z = pos2Field.z().trim();
        boolean hasPos2 = !pos2X.isEmpty() || !pos2Y.isEmpty() || !pos2Z.isEmpty();
        Integer px2 = null, py2 = null, pz2 = null;
        if (hasPos2) {
            if (pos2X.isEmpty() || pos2Y.isEmpty() || pos2Z.isEmpty()) {
                errorMessage = UIText.of("ponderer.ui." + stepType + ".error.partial_to");
                return null;
            }
            px2 = parseInt(pos2X, "X2");
            py2 = parseInt(pos2Y, "Y2");
            pz2 = parseInt(pos2Z, "Z2");
            if (px2 == null || py2 == null || pz2 == null) return null;
        }

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = stepType;
        s.blockPos = List.of(px, py, pz);
        if (hasPos2) s.blockPos2 = List.of(px2, py2, pz2);

        if (withDirection) {
            s.direction = DIRECTIONS[directionIndex];
        }

        if (withLinkId) {
            String linkId = linkIdField.getValue().trim();
            if (!linkId.isEmpty()) s.linkId = linkId;
        }

        if (supportsEntranceAnimation()) {
            String entranceAnimation = ENTRANCE_ANIMATIONS[entranceAnimationIndex];
            if ("none".equals(entranceAnimation)) {
                s.entranceAnimation = "none";
                s.duration = 0;
            } else {
                s.entranceAnimation = entranceAnimation;
                s.entranceDuration = Math.max(0, parseIntOr(durationField.getValue(), 20));
                s.entranceInterval = Math.max(0, parseIntOr(intervalField.getValue(), 1));
            }
            s.smartDisplay = smartDisplay;
        }

        if (withDuration && s.entranceAnimation == null) {
            s.duration = Math.max(0, parseIntOr(durationField.getValue(), 20));
        }

        return s;
    }
}
