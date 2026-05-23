package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class DestroyBlockScreen extends AbstractStepEditorScreen {

    private final StepXyzFieldHandle posField = new StepXyzFieldHandle("pos");
    private final StepXyzFieldHandle pos2Field = new StepXyzFieldHandle("pos2");
    private boolean destroyParticles = true;

    public DestroyBlockScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.destroy_block.add"), scene, sceneIndex, parent);
    }

    public DestroyBlockScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                              int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.destroy_block.edit"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override
    protected String getHeaderTitle() {
        return UIText.of("ponderer.ui.destroy_block");
    }

    @Override
    protected void collectStepEntries(List<com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.xyz(
            posField,
            "ponderer.ui.destroy_block.pos_from",
            "ponderer.ui.destroy_block.pos_from.tooltip",
            "X",
            "Y",
            "Z",
            FieldDecorators.pointPick(PickState.TargetField.POS1)));
        entries.add(FieldSpecs.xyz(
            pos2Field,
            "ponderer.ui.destroy_block.pos_to",
            "ponderer.ui.destroy_block.pos_to.tooltip",
            "X",
            "Y",
            "Z",
            FieldDecorators.pointPick(PickState.TargetField.POS2)));
        entries.add(FieldSpecs.toggle(
            "ponderer.ui.destroy_block.particles",
            "ponderer.ui.destroy_block.particles.tooltip",
            () -> destroyParticles,
            () -> destroyParticles = !destroyParticles));
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
        if (step.destroyParticles != null) {
            destroyParticles = step.destroyParticles;
        }
    }

    @Override
    protected String getStepType() { return "destroy_block"; }

    @Override
    protected void appendCustomSnapshot(Map<String, String> snapshot) {
        snapshot.put("particles", String.valueOf(destroyParticles));
    }

    @Override
    protected void restoreCustomSnapshot(Map<String, String> snapshot) {
        if (snapshot.containsKey("particles")) {
            destroyParticles = Boolean.parseBoolean(snapshot.get("particles"));
        }
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        clearStatusMessages();

        FormParsers.ParseResult<FormParsers.IntRange> range = FormParsers.intRange(
            posField,
            pos2Field,
            UIText.of("ponderer.ui.destroy_block.error.partial_to"));
        if (range.failed()) {
            setErrorMessage(range.errorMessage());
            return null;
        }

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "destroy_block";
        s.blockPos = range.value().from().toList();
        if (range.value().to() != null) {
            s.blockPos2 = range.value().to().toList();
        }
        if (!destroyParticles) s.destroyParticles = false;
        return s;
    }
}
