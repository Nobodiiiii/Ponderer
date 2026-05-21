package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;

public class SelectionOperationScreen extends AbstractStepEditorScreen {

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
    private boolean smartDisplay = false;

    private final FieldBinding<Integer> directionBinding =
        FieldBindings.integer("direction", () -> directionIndex, value -> directionIndex = value);
    private final FieldBinding<Integer> entranceAnimationBinding =
        FieldBindings.integer("entranceAnimation", () -> entranceAnimationIndex, value -> entranceAnimationIndex = value);
    private final FieldBinding<Boolean> smartDisplayBinding =
        FieldBindings.bool("smartDisplay", () -> smartDisplay, value -> smartDisplay = Boolean.TRUE.equals(value));

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

    @Override
    protected void configureFormState(List<SnapshotParticipant> participants) {
        if (withDirection) {
            participants.add(directionBinding);
        }
        if (supportsEntranceAnimation()) {
            participants.add(entranceAnimationBinding);
            participants.add(smartDisplayBinding);
        }
    }

    private boolean supportsEntranceAnimation() {
        return "show_section_and_merge".equals(stepType);
    }

    @Override
    protected String getHeaderTitle() {
        return UIText.of("ponderer.ui." + stepType);
    }

    @Override
    protected void collectStepEntries(List<com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.xyz(
            posField,
            "ponderer.ui." + stepType + ".pos_from",
            "ponderer.ui." + stepType + ".pos_from.tooltip",
            "X",
            "Y",
            "Z",
            FieldDecorators.pointPick(PickState.TargetField.POS1)));
        entries.add(FieldSpecs.xyz(
            pos2Field,
            "ponderer.ui." + stepType + ".pos_to",
            "ponderer.ui." + stepType + ".pos_to.tooltip",
            "X",
            "Y",
            "Z",
            FieldDecorators.pointPick(PickState.TargetField.POS2)));
        if (supportsEntranceAnimation()) {
            entries.add(FieldSpecs.cycle(
                entranceAnimationBinding,
                "ponderer.ui.entrance_animation",
                "ponderer.ui.entrance_animation.tooltip",
                140,
                SelectionAnimationOptions.ENTRANCE_ANIMATIONS.length,
                this::updateTickFieldsEnabledState,
                () -> SelectionAnimationOptions.entranceAnimationLabel(
                    SelectionAnimationOptions.ENTRANCE_ANIMATIONS[entranceAnimationIndex]),
                () -> 0xFFFFFF));
        }
        if (withDirection) {
            entries.add(FieldSpecs.cycle(
                directionBinding,
                "ponderer.ui." + stepType + ".direction",
                "ponderer.ui." + stepType + ".direction.tooltip",
                140,
                SelectionAnimationOptions.DIRECTIONS.length,
                () -> {
                },
                () -> SelectionAnimationOptions.optionLabel(
                    "ponderer.ui.show_controls.direction",
                    SelectionAnimationOptions.DIRECTIONS[directionIndex]),
                () -> 0xFFFFFF));
        }
        if (withLinkId) {
            entries.add(FieldSpecs.text(
                linkIdField,
                "ponderer.ui." + stepType + ".link",
                "ponderer.ui." + stepType + ".link.tooltip",
                "",
                140));
        }
        if (withDuration) {
            entries.add(FieldSpecs.ticksNumber(
                durationField,
                "ponderer.ui.duration",
                "ponderer.ui.duration.tooltip.section_animation",
                "20",
                60));
        }
        if (supportsEntranceAnimation()) {
            entries.add(FieldSpecs.ticksNumber(
                intervalField,
                "ponderer.ui.entrance_interval",
                "ponderer.ui.entrance_interval.tooltip",
                "1",
                60));
            entries.add(FieldSpecs.toggle(
                smartDisplayBinding,
                "ponderer.ui.smart_display",
                "ponderer.ui.smart_display.tooltip"));
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
            String normalized = SelectionAnimationOptions.normalizeDirection(step.direction);
            for (int i = 0; i < SelectionAnimationOptions.DIRECTIONS.length; i++) {
                if (SelectionAnimationOptions.DIRECTIONS[i].equals(normalized)) {
                    directionIndex = i;
                    break;
                }
            }
        }
        if (withLinkId && step.linkId != null) {
            linkIdField.setValue(step.linkId);
        }
        if (supportsEntranceAnimation() && step.entranceAnimation != null && !step.entranceAnimation.isBlank()) {
            String normalized = SelectionAnimationOptions.normalizeEntranceAnimation(step.entranceAnimation);
            for (int i = 0; i < SelectionAnimationOptions.ENTRANCE_ANIMATIONS.length; i++) {
                if (SelectionAnimationOptions.ENTRANCE_ANIMATIONS[i].equals(normalized)) {
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

    private void updateTickFieldsEnabledState() {
        if (!supportsEntranceAnimation()) {
            return;
        }
        String mode = SelectionAnimationOptions.ENTRANCE_ANIMATIONS[entranceAnimationIndex];
        // duration controls the fade-in even when no row pattern is selected
        // ("none" still wants the standard fade-in via step.duration).
        boolean durationEnabled = true;
        boolean intervalEnabled = !("none".equals(mode) || "simultaneous".equals(mode));

        if (durationField.widget() != null) {
            durationField.widget().active = durationEnabled;
        }
        if (intervalField.widget() != null) {
            intervalField.widget().active = intervalEnabled;
        }
    }

    @Override
    protected String getStepType() {
        return stepType;
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        clearStatusMessages();

        FormParsers.ParseResult<FormParsers.IntRange> range = FormParsers.intRange(
            posField,
            pos2Field,
            UIText.of("ponderer.ui." + stepType + ".error.partial_to"));
        if (range.failed()) {
            setErrorMessage(range.errorMessage());
            return null;
        }

        DslScene.DslStep step = new DslScene.DslStep();
        step.type = stepType;
        step.blockPos = range.value().from().toList();
        if (range.value().to() != null) {
            step.blockPos2 = range.value().to().toList();
        }

        if (withDirection) {
            step.direction = SelectionAnimationOptions.DIRECTIONS[directionIndex];
        }

        if (withLinkId) {
            String linkId = linkIdField.getValue().trim();
            if (!linkId.isEmpty()) {
                step.linkId = linkId;
            }
        }

        if (supportsEntranceAnimation()) {
            String entranceAnimation = SelectionAnimationOptions.ENTRANCE_ANIMATIONS[entranceAnimationIndex];
            if ("none".equals(entranceAnimation)) {
                // "(无)" 入场模式：不使用逐排动画，由 step.duration 控制标准淡入时长。
                // 让外层 `withDuration && step.entranceAnimation == null` 分支负责写入 step.duration。
                step.entranceAnimation = null;
            } else {
                step.entranceAnimation = entranceAnimation;
                step.entranceDuration = Math.max(0, parseIntOr(durationField.getValue(), 20));
                step.entranceInterval = Math.max(0, parseIntOr(intervalField.getValue(), 1));
                step.smartDisplay = smartDisplay;
            }
        }

        if (withDuration && step.entranceAnimation == null) {
            step.duration = Math.max(0, parseIntOr(durationField.getValue(), 20));
        }

        return step;
    }
}
