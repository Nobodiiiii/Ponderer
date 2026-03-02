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

/**
 * Shared editor screen for clear_item_entities and clear_entities step types.
 */
public class ClearEntitiesScreen extends AbstractStepEditorScreen {

    private final String stepType;
    private final IdFieldMode jeiMode;

    private HintableTextFieldWidget idField;
    private HintableTextFieldWidget posXField, posYField, posZField;
    private HintableTextFieldWidget pos2XField, pos2YField, pos2ZField;
    private boolean fullScene = false;
    private BoxWidget fullSceneToggle;
    private PonderButton pickBtn1, pickBtn2;
    @Nullable
    private PonderButton jeiBtn;

    public ClearEntitiesScreen(String stepType, DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui." + stepType + ".add"), scene, sceneIndex, parent);
        this.stepType = stepType;
        this.jeiMode = "clear_item_entities".equals(stepType) ? IdFieldMode.ITEM : IdFieldMode.ENTITY;
    }

    public ClearEntitiesScreen(String stepType, DslScene scene, int sceneIndex, SceneEditorScreen parent,
                               int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui." + stepType + ".edit"), scene, sceneIndex, parent, editIndex, step);
        this.stepType = stepType;
        this.jeiMode = "clear_item_entities".equals(stepType) ? IdFieldMode.ITEM : IdFieldMode.ENTITY;
    }

    @Override
    protected int getFormRowCount() { return 4; }

    @Override
    protected String getHeaderTitle() { return UIText.of("ponderer.ui." + stepType); }

    @Override
    protected void buildForm() {
        beginForm();
        var jei = addFormTextFieldWithJei("ponderer.ui." + stepType + ".id", "ponderer.ui." + stepType + ".id.tooltip",
                UIText.of("ponderer.ui." + stepType + ".id.hint"), jeiMode);
        idField = jei.field(); jeiBtn = jei.jeiBtn();
        var pos1 = addFormXyzRow("ponderer.ui." + stepType + ".pos_from", "ponderer.ui." + stepType + ".pos_from.tooltip", PickState.TargetField.POS1);
        posXField = pos1.x(); posYField = pos1.y(); posZField = pos1.z(); pickBtn1 = pos1.pickBtn();
        var pos2 = addFormXyzRow("ponderer.ui." + stepType + ".pos_to", "ponderer.ui." + stepType + ".pos_to.tooltip", PickState.TargetField.POS2);
        pos2XField = pos2.x(); pos2YField = pos2.y(); pos2ZField = pos2.z(); pickBtn2 = pos2.pickBtn();
        fullSceneToggle = addFormToggle("ponderer.ui." + stepType + ".full_scene", "ponderer.ui." + stepType + ".full_scene.tooltip",
                () -> fullScene, () -> fullScene = !fullScene);
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.item != null) idField.setValue(step.item);
        if (step.entity != null) idField.setValue(step.entity);
        if (step.blockPos != null && step.blockPos.size() >= 3) {
            posXField.setValue(String.valueOf(step.blockPos.get(0)));
            posYField.setValue(String.valueOf(step.blockPos.get(1)));
            posZField.setValue(String.valueOf(step.blockPos.get(2)));
        }
        if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
            pos2XField.setValue(String.valueOf(step.blockPos2.get(0)));
            pos2YField.setValue(String.valueOf(step.blockPos2.get(1)));
            pos2ZField.setValue(String.valueOf(step.blockPos2.get(2)));
        }
        if (step.fullScene != null) fullScene = step.fullScene;
    }

    @Override
    protected String getStepType() { return stepType; }

    @Override
    protected Map<String, String> snapshotForm() {
        Map<String, String> m = new HashMap<>();
        m.put("id", idField.getValue());
        m.put("posX", posXField.getValue());
        m.put("posY", posYField.getValue());
        m.put("posZ", posZField.getValue());
        m.put("pos2X", pos2XField.getValue());
        m.put("pos2Y", pos2YField.getValue());
        m.put("pos2Z", pos2ZField.getValue());
        m.put("fullScene", String.valueOf(fullScene));
        return m;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        if (snapshot.containsKey("id")) idField.setValue(snapshot.get("id"));
        if (snapshot.containsKey("posX")) posXField.setValue(snapshot.get("posX"));
        if (snapshot.containsKey("posY")) posYField.setValue(snapshot.get("posY"));
        if (snapshot.containsKey("posZ")) posZField.setValue(snapshot.get("posZ"));
        if (snapshot.containsKey("pos2X")) pos2XField.setValue(snapshot.get("pos2X"));
        if (snapshot.containsKey("pos2Y")) pos2YField.setValue(snapshot.get("pos2Y"));
        if (snapshot.containsKey("pos2Z")) pos2ZField.setValue(snapshot.get("pos2Z"));
        if (snapshot.containsKey("fullScene")) fullScene = Boolean.parseBoolean(snapshot.get("fullScene"));
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;

        Integer px = null, py = null, pz = null;
        Integer px2 = null, py2 = null, pz2 = null;

        if (!fullScene) {
            px = parseInt(posXField.getValue(), "X");
            py = parseInt(posYField.getValue(), "Y");
            pz = parseInt(posZField.getValue(), "Z");
            if (px == null || py == null || pz == null) return null;

            String pos2X = pos2XField.getValue().trim();
            String pos2Y = pos2YField.getValue().trim();
            String pos2Z = pos2ZField.getValue().trim();
            boolean hasPos2 = !pos2X.isEmpty() || !pos2Y.isEmpty() || !pos2Z.isEmpty();
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
        }

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = stepType;
        String id = idField.getValue().trim();
        if (!id.isEmpty()) {
            if ("clear_item_entities".equals(stepType)) {
                s.item = id;
            } else {
                s.entity = id;
            }
        }
        if (fullScene) {
            s.fullScene = true;
        } else {
            s.blockPos = List.of(px, py, pz);
            if (px2 != null) s.blockPos2 = List.of(px2, py2, pz2);
        }
        return s;
    }
}
