package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class DestroyBlockScreen extends AbstractStepEditorScreen {

    private final StepXyzFieldHandle posField = new StepXyzFieldHandle("pos");
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
        entries.add(StepEditorEntries.xyz(
            posField,
            "ponderer.ui.destroy_block.pos",
            "ponderer.ui.destroy_block.pos.tooltip",
            PickState.TargetField.POS1));
        entries.add(StepEditorEntries.toggle(
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
        errorMessage = null;

        Integer px = parseInt(posField.x(), "X");
        Integer py = parseInt(posField.y(), "Y");
        Integer pz = parseInt(posField.z(), "Z");
        if (px == null || py == null || pz == null) return null;

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "destroy_block";
        s.blockPos = List.of(px, py, pz);
        if (!destroyParticles) s.destroyParticles = false;
        return s;
    }
}
