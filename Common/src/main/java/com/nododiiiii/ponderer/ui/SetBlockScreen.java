package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class SetBlockScreen extends AbstractStepEditorScreen {

    private static final String[] ENTRANCE_MODES = {"hidden", "immediate", "animated"};

    private final StepTextFieldHandle blockField = new StepTextFieldHandle("block");
    private final StepTextFieldHandle nbtField = new StepTextFieldHandle("nbt");
    private final StepXyzFieldHandle posField = new StepXyzFieldHandle("pos");
    private final StepXyzFieldHandle pos2Field = new StepXyzFieldHandle("pos2");
    private final StepTextFieldHandle linkIdField = new StepTextFieldHandle("linkId");
    private final StepTextFieldHandle durationField = new StepTextFieldHandle("duration");
    private final StepTextFieldHandle intervalField = new StepTextFieldHandle("entranceInterval");
    private final KeyValueListState blockProperties = new KeyValueListState("prop", 1);

    private boolean spawnParticles = true;
    private boolean smartDisplay = true;
    private int entranceModeIndex = 1;
    private int directionIndex = 0;
    private int entranceAnimationIndex = 0;

    private final FieldBinding<Boolean> spawnParticlesBinding =
        FieldBindings.bool("particles", () -> spawnParticles, value -> spawnParticles = Boolean.TRUE.equals(value));
    private final FieldBinding<Boolean> smartDisplayBinding =
        FieldBindings.bool("smartDisplay", () -> smartDisplay, value -> smartDisplay = Boolean.TRUE.equals(value));
    private final FieldBinding<Integer> entranceModeBinding =
        FieldBindings.integer("entranceMode", () -> entranceModeIndex, value -> entranceModeIndex = value);
    private final FieldBinding<Integer> directionBinding =
        FieldBindings.integer("direction", () -> directionIndex, value -> directionIndex = value);
    private final FieldBinding<Integer> entranceAnimationBinding =
        FieldBindings.integer("entranceAnimation", () -> entranceAnimationIndex, value -> entranceAnimationIndex = value);

    public SetBlockScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.set_block.add"), scene, sceneIndex, parent);
    }

    public SetBlockScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                          int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.set_block.edit"), scene, sceneIndex, parent, editIndex, step);
        boolean animatedMode = step != null
            && step.entranceAnimation != null
            && !step.entranceAnimation.isBlank()
            && !"none".equals(SelectionAnimationOptions.normalizeEntranceAnimation(step.entranceAnimation));
        if (animatedMode) {
            entranceModeIndex = 2;
        } else if (step != null && Boolean.FALSE.equals(step.immediateDisplay)) {
            entranceModeIndex = 0;
        } else {
            entranceModeIndex = 1;
        }
    }

    @Override
    protected void configureFormState(List<SnapshotParticipant> participants) {
        participants.add(blockProperties);
        participants.add(spawnParticlesBinding);
        participants.add(smartDisplayBinding);
        participants.add(entranceModeBinding);
        participants.add(directionBinding);
        participants.add(entranceAnimationBinding);
    }

    @Override
    protected String getHeaderTitle() {
        return UIText.of("ponderer.ui.set_block");
    }

    @Override
    protected void collectStepEntries(List<com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.text(
            blockField,
            "ponderer.ui.set_block",
            "ponderer.ui.set_block.tooltip",
            UIText.of("ponderer.ui.set_block.hint"),
            124,
            FieldDecorators.jei(IdFieldMode.BLOCK),
            FieldDecorators.blockPick("nbt")));
        entries.add(FieldSpecs.blockProperties(
            blockProperties,
            "ponderer.ui.block_properties",
            "ponderer.ui.block_properties.tooltip"));
        entries.add(FieldSpecs.text(
            nbtField,
            "ponderer.ui.set_block.nbt",
            "ponderer.ui.set_block.nbt.tooltip",
            "{CustomName:'\"Demo\"'}",
            124,
            FieldDecorators.nbtPick("nbt"),
            FieldDecorators.nbtExpand("nbt")));
        entries.add(FieldSpecs.xyz(
            posField,
            "ponderer.ui.set_block.pos_from",
            "ponderer.ui.set_block.pos_from.tooltip",
            "X",
            "Y",
            "Z",
            FieldDecorators.pointPick(PickState.TargetField.POS1)));
        entries.add(FieldSpecs.xyz(
            pos2Field,
            "ponderer.ui.set_block.pos_to",
            "ponderer.ui.set_block.pos_to.tooltip",
            "X",
            "Y",
            "Z",
            FieldDecorators.pointPick(PickState.TargetField.POS2)));
        entries.add(FieldSpecs.cycle(
            entranceModeBinding,
            "ponderer.ui.set_block.entrance_mode",
            "ponderer.ui.set_block.entrance_mode.tooltip",
            140,
            ENTRANCE_MODES.length,
            this::rebuildFormPreservingState,
            () -> UIText.of("ponderer.ui.set_block.entrance_mode.option." + ENTRANCE_MODES[entranceModeIndex]),
            () -> 0xFFFFFF));

        String mode = ENTRANCE_MODES[entranceModeIndex];
        if ("immediate".equals(mode)) {
            entries.add(FieldSpecs.toggle(
                spawnParticlesBinding,
                "ponderer.ui.set_block.particles",
                "ponderer.ui.set_block.particles.tooltip"));
        } else if ("animated".equals(mode)) {
            entries.add(FieldSpecs.cycle(
                entranceAnimationBinding,
                "ponderer.ui.set_block.entrance_animation",
                "ponderer.ui.set_block.entrance_animation.tooltip",
                140,
                SelectionAnimationOptions.ENTRANCE_ANIMATIONS.length,
                () -> {
                },
                () -> SelectionAnimationOptions.entranceAnimationLabel(
                    SelectionAnimationOptions.ENTRANCE_ANIMATIONS[entranceAnimationIndex]),
                () -> 0xFFFFFF));
            entries.add(FieldSpecs.cycle(
                directionBinding,
                "ponderer.ui.show_section_and_merge.direction",
                "ponderer.ui.show_section_and_merge.direction.tooltip",
                140,
                SelectionAnimationOptions.DIRECTIONS.length,
                () -> {
                },
                () -> SelectionAnimationOptions.optionLabel(
                    "ponderer.ui.show_controls.direction",
                    SelectionAnimationOptions.DIRECTIONS[directionIndex]),
                () -> 0xFFFFFF));
            entries.add(FieldSpecs.text(
                linkIdField,
                "ponderer.ui.show_section_and_merge.link",
                "ponderer.ui.show_section_and_merge.link.tooltip",
                "",
                140));
            entries.add(FieldSpecs.ticksNumber(
                durationField,
                "ponderer.ui.duration",
                "ponderer.ui.duration.tooltip.section_animation",
                "20",
                60));
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
        }
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.block != null) {
            blockField.setValue(step.block);
        }
        if (step.blockPos != null && step.blockPos.size() >= 3) {
            posField.setValue(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
        }
        if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
            pos2Field.setValue(step.blockPos2.get(0), step.blockPos2.get(1), step.blockPos2.get(2));
        }
        blockProperties.replaceFromMap(step.blockProperties);
        if (step.spawnParticles != null) {
            spawnParticles = step.spawnParticles;
        }
        if (step.smartDisplay != null) {
            smartDisplay = step.smartDisplay;
        }
        if (step.entranceAnimation != null && !step.entranceAnimation.isBlank()) {
            String normalized = SelectionAnimationOptions.normalizeEntranceAnimation(step.entranceAnimation);
            for (int i = 0; i < SelectionAnimationOptions.ENTRANCE_ANIMATIONS.length; i++) {
                if (SelectionAnimationOptions.ENTRANCE_ANIMATIONS[i].equals(normalized)) {
                    entranceAnimationIndex = i;
                    break;
                }
            }
        }
        if (step.direction != null) {
            String normalized = SelectionAnimationOptions.normalizeDirection(step.direction);
            for (int i = 0; i < SelectionAnimationOptions.DIRECTIONS.length; i++) {
                if (SelectionAnimationOptions.DIRECTIONS[i].equals(normalized)) {
                    directionIndex = i;
                    break;
                }
            }
        }
        if (step.linkId != null) {
            linkIdField.setValue(step.linkId);
        }
        if (step.entranceDuration != null) {
            durationField.setValue(String.valueOf(step.entranceDuration));
        } else if (step.duration != null) {
            durationField.setValue(String.valueOf(step.duration));
        }
        if (step.entranceInterval != null) {
            intervalField.setValue(String.valueOf(step.entranceInterval));
        }
        if (step.nbt != null) {
            nbtField.setValue(step.nbt);
        }
    }

    @Override
    protected String getStepType() {
        return "set_block";
    }

    @Override
    protected void restoreCustomSnapshot(Map<String, String> snapshot) {
        if (snapshot.containsKey(NbtPickState.SNAPSHOT_BLOCK_ID_KEY)) {
            blockField.setValue(snapshot.get(NbtPickState.SNAPSHOT_BLOCK_ID_KEY));
        }
        restoreNbtPickNotice(snapshot);
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        clearStatusMessages();

        FormParsers.ParseResult<String> blockId = FormParsers.registryId(
            blockField.getValue(),
            UIText.of("ponderer.ui.set_block.error.required"),
            UIText.of("ponderer.ui.set_block.error.invalid_id"),
            value -> UIText.of("ponderer.ui.set_block.error.unknown", value),
            BuiltInRegistries.BLOCK);
        if (blockId.failed()) {
            setErrorMessage(blockId.errorMessage());
            return null;
        }

        FormParsers.ParseResult<FormParsers.IntRange> range = FormParsers.intRange(
            posField,
            pos2Field,
            UIText.of("ponderer.ui.set_block.error.partial_to"));
        if (range.failed()) {
            setErrorMessage(range.errorMessage());
            return null;
        }

        FormParsers.ParseResult<String> nbt = FormParsers.optionalNbt(
            nbtField.getValue(),
            UIText.of("ponderer.ui.modify_block_entity_nbt.error.invalid"));
        if (nbt.failed()) {
            setErrorMessage(nbt.errorMessage());
            return null;
        }

        DslScene.DslStep step = new DslScene.DslStep();
        step.type = "set_block";
        step.block = blockId.value();
        step.blockProperties = blockProperties.toFilteredMap();
        step.blockPos = range.value().from().toList();
        if (range.value().to() != null) {
            step.blockPos2 = range.value().to().toList();
        }
        if (nbt.value() != null) {
            step.nbt = nbt.value();
        }
        if (!spawnParticles) {
            step.spawnParticles = false;
        }

        String entranceMode = ENTRANCE_MODES[entranceModeIndex];
        if ("hidden".equals(entranceMode)) {
            step.immediateDisplay = false;
            step.spawnParticles = false;
            step.entranceAnimation = "none";
        } else if ("immediate".equals(entranceMode)) {
            step.immediateDisplay = true;
            step.entranceAnimation = "none";
            if (!spawnParticles) {
                step.spawnParticles = false;
            }
        } else {
            step.immediateDisplay = false;
            step.spawnParticles = false;
            step.direction = SelectionAnimationOptions.DIRECTIONS[directionIndex];
            String linkId = linkIdField.getValue().trim();
            if (!linkId.isEmpty()) {
                step.linkId = linkId;
            }
            String entranceAnimation = SelectionAnimationOptions.ENTRANCE_ANIMATIONS[entranceAnimationIndex];
            step.entranceAnimation = "none".equals(entranceAnimation) ? "down" : entranceAnimation;
            step.entranceDuration = Math.max(0, parseIntOr(durationField.getValue(), 20));
            step.entranceInterval = Math.max(0, parseIntOr(intervalField.getValue(), 1));
            step.smartDisplay = smartDisplay;
        }

        return step;
    }
}
