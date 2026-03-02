package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DestroyBlockScreen extends AbstractStepEditorScreen {

    private net.createmod.catnip.config.ui.HintableTextFieldWidget posXField, posYField, posZField;
    private boolean destroyParticles = true;
    private BoxWidget particlesToggle;
    private PonderButton pickBtn1;

    public DestroyBlockScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.destroy_block.add"), scene, sceneIndex, parent);
    }

    public DestroyBlockScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                              int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.destroy_block.edit"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override
    protected int getFormRowCount() {
        return 2;
    }

    @Override
    protected String getHeaderTitle() {
        return UIText.of("ponderer.ui.destroy_block");
    }

    @Override
    protected void buildForm() {
        beginForm();
        var pos = addFormXyzRow("ponderer.ui.destroy_block.pos", "ponderer.ui.destroy_block.pos.tooltip", PickState.TargetField.POS1);
        posXField = pos.x();
        posYField = pos.y();
        posZField = pos.z();
        pickBtn1 = pos.pickBtn();
        particlesToggle = addFormToggle("ponderer.ui.destroy_block.particles", "ponderer.ui.destroy_block.particles.tooltip",
                () -> destroyParticles, () -> destroyParticles = !destroyParticles);
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.blockPos != null && step.blockPos.size() >= 3) {
            posXField.setValue(String.valueOf(step.blockPos.get(0)));
            posYField.setValue(String.valueOf(step.blockPos.get(1)));
            posZField.setValue(String.valueOf(step.blockPos.get(2)));
        }
        if (step.destroyParticles != null) {
            destroyParticles = step.destroyParticles;
        }
    }

    @Override
    protected String getStepType() { return "destroy_block"; }

    @Override
    protected Map<String, String> snapshotForm() {
        Map<String, String> m = new HashMap<>();
        m.put("posX", posXField.getValue());
        m.put("posY", posYField.getValue());
        m.put("posZ", posZField.getValue());
        m.put("particles", String.valueOf(destroyParticles));
        return m;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        if (snapshot.containsKey("posX")) posXField.setValue(snapshot.get("posX"));
        if (snapshot.containsKey("posY")) posYField.setValue(snapshot.get("posY"));
        if (snapshot.containsKey("posZ")) posZField.setValue(snapshot.get("posZ"));
        if (snapshot.containsKey("particles")) destroyParticles = Boolean.parseBoolean(snapshot.get("particles"));
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;

        Integer px = parseInt(posXField.getValue(), "X");
        Integer py = parseInt(posYField.getValue(), "Y");
        Integer pz = parseInt(posZField.getValue(), "Z");
        if (px == null || py == null || pz == null) return null;

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "destroy_block";
        s.blockPos = List.of(px, py, pz);
        if (!destroyParticles) s.destroyParticles = false;
        return s;
    }
}
