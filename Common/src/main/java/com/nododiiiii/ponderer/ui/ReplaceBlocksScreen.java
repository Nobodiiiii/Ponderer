package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;

public class ReplaceBlocksScreen extends AbstractStepEditorScreen {

    private final StepTextFieldHandle blockField = new StepTextFieldHandle("block");
    private final StepXyzFieldHandle posField = new StepXyzFieldHandle("pos");
    private final StepXyzFieldHandle pos2Field = new StepXyzFieldHandle("pos2");
    private final KeyValueListState blockProperties = new KeyValueListState("prop", 1);

    private boolean spawnParticles = true;
    private final FieldBinding<Boolean> spawnParticlesBinding =
        FieldBindings.bool("particles", () -> spawnParticles, value -> spawnParticles = Boolean.TRUE.equals(value));

    public ReplaceBlocksScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.replace_blocks.add"), scene, sceneIndex, parent);
    }

    public ReplaceBlocksScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                               int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.replace_blocks.edit"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override
    protected void configureFormState(List<SnapshotParticipant> participants) {
        participants.add(blockProperties);
        participants.add(spawnParticlesBinding);
    }

    @Override
    protected String getHeaderTitle() {
        return UIText.of("ponderer.ui.replace_blocks");
    }

    @Override
    protected void collectStepEntries(List<com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.text(
            blockField,
            "ponderer.ui.replace_blocks",
            "ponderer.ui.replace_blocks.tooltip",
            UIText.of("ponderer.ui.replace_blocks.hint"),
            124,
            FieldDecorators.jei(IdFieldMode.BLOCK)));
        entries.add(FieldSpecs.blockProperties(
            blockProperties,
            "ponderer.ui.block_properties",
            "ponderer.ui.block_properties.tooltip"));
        entries.add(FieldSpecs.xyz(
            posField,
            "ponderer.ui.replace_blocks.pos_from",
            "ponderer.ui.replace_blocks.pos_from.tooltip",
            "X",
            "Y",
            "Z",
            FieldDecorators.pointPick(PickState.TargetField.POS1)));
        entries.add(FieldSpecs.xyz(
            pos2Field,
            "ponderer.ui.replace_blocks.pos_to",
            "ponderer.ui.replace_blocks.pos_to.tooltip",
            "X",
            "Y",
            "Z",
            FieldDecorators.pointPick(PickState.TargetField.POS2)));
        entries.add(FieldSpecs.toggle(
            spawnParticlesBinding,
            "ponderer.ui.replace_blocks.particles",
            "ponderer.ui.replace_blocks.particles.tooltip"));
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
    }

    @Override
    protected String getStepType() {
        return "replace_blocks";
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;

        FormParsers.ParseResult<String> blockId = FormParsers.registryId(
            blockField.getValue(),
            UIText.of("ponderer.ui.replace_blocks.error.required"),
            UIText.of("ponderer.ui.replace_blocks.error.invalid_id"),
            value -> UIText.of("ponderer.ui.replace_blocks.error.unknown", value),
            BuiltInRegistries.BLOCK);
        if (blockId.failed()) {
            errorMessage = blockId.errorMessage();
            return null;
        }

        FormParsers.ParseResult<FormParsers.IntRange> range = FormParsers.intRange(
            posField,
            pos2Field,
            UIText.of("ponderer.ui.replace_blocks.error.partial_to"));
        if (range.failed()) {
            errorMessage = range.errorMessage();
            return null;
        }

        DslScene.DslStep step = new DslScene.DslStep();
        step.type = "replace_blocks";
        step.block = blockId.value();
        step.blockProperties = blockProperties.toFilteredMap();
        step.blockPos = range.value().from().toList();
        if (range.value().to() != null) {
            step.blockPos2 = range.value().to().toList();
        }
        if (!spawnParticles) {
            step.spawnParticles = false;
        }
        return step;
    }
}
