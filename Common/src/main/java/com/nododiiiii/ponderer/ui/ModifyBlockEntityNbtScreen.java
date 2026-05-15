package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class ModifyBlockEntityNbtScreen extends AbstractStepEditorScreen {

    private final StepXyzFieldHandle posField = new StepXyzFieldHandle("pos");
    private final StepXyzFieldHandle pos2Field = new StepXyzFieldHandle("pos2");
    private final StepTextFieldHandle nbtField = new StepTextFieldHandle("nbt");
    private final KeyValueListState blockProperties = new KeyValueListState("prop", 1);

    private boolean redraw = false;
    private final FieldBinding<Boolean> redrawBinding =
        FieldBindings.bool("redraw", () -> redraw, value -> redraw = Boolean.TRUE.equals(value));

    public ModifyBlockEntityNbtScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.modify_block_entity_nbt.add"), scene, sceneIndex, parent);
    }

    public ModifyBlockEntityNbtScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                      int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.modify_block_entity_nbt.edit"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override
    protected void configureFormState(List<SnapshotParticipant> participants) {
        participants.add(blockProperties);
        participants.add(redrawBinding);
    }

    @Override
    protected String getHeaderTitle() {
        return UIText.of("ponderer.ui.modify_block_entity_nbt");
    }

    @Override
    protected void collectStepEntries(List<com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.xyz(
            posField,
            "ponderer.ui.modify_block_entity_nbt.pos_from",
            "ponderer.ui.modify_block_entity_nbt.pos_from.tooltip",
            "X",
            "Y",
            "Z",
            FieldDecorators.pointPick(PickState.TargetField.POS1)));
        entries.add(FieldSpecs.xyz(
            pos2Field,
            "ponderer.ui.modify_block_entity_nbt.pos_to",
            "ponderer.ui.modify_block_entity_nbt.pos_to.tooltip",
            "X",
            "Y",
            "Z",
            FieldDecorators.pointPick(PickState.TargetField.POS2)));
        entries.add(FieldSpecs.blockProperties(
            blockProperties,
            "ponderer.ui.modify_block_entity_nbt.properties",
            "ponderer.ui.modify_block_entity_nbt.properties.tooltip"));
        entries.add(FieldSpecs.text(
            nbtField,
            "ponderer.ui.modify_block_entity_nbt.nbt",
            "ponderer.ui.modify_block_entity_nbt.nbt.tooltip",
            "{CustomName:'\"Demo\"'}",
            124,
            FieldDecorators.nbtPick("nbt"),
            FieldDecorators.nbtExpand("nbt")));
        entries.add(FieldSpecs.toggle(
            redrawBinding,
            "ponderer.ui.modify_block_entity_nbt.redraw",
            "ponderer.ui.modify_block_entity_nbt.redraw.tooltip"));
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
        blockProperties.replaceFromMap(step.blockProperties);
        if (step.nbt != null) {
            nbtField.setValue(step.nbt);
        }
        if (step.reDrawBlocks != null) {
            redraw = step.reDrawBlocks;
        }
    }

    @Override
    protected String getStepType() {
        return "modify_block_entity_nbt";
    }

    @Override
    protected void restoreCustomSnapshot(Map<String, String> snapshot) {
        restoreNbtPickNotice(snapshot);
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        clearStatusMessages();

        FormParsers.ParseResult<FormParsers.IntRange> range = FormParsers.intRange(
            posField,
            pos2Field,
            UIText.of("ponderer.ui.modify_block_entity_nbt.error.partial_to"));
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

        Map<String, String> props = blockProperties.toFilteredMap();
        if (nbt.value() == null && props == null) {
            setErrorMessage(UIText.of("ponderer.ui.modify_block_entity_nbt.error.required"));
            return null;
        }

        DslScene.DslStep step = new DslScene.DslStep();
        step.type = "modify_block_entity_nbt";
        step.blockPos = range.value().from().toList();
        if (range.value().to() != null) {
            step.blockPos2 = range.value().to().toList();
        }
        step.blockProperties = props;
        if (nbt.value() != null) {
            step.nbt = nbt.value();
        }
        if (redraw) {
            step.reDrawBlocks = true;
        }
        return step;
    }
}
