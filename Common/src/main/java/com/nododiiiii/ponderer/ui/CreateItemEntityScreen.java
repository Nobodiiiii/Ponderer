package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreateItemEntityScreen extends AbstractStepEditorScreen {

    private HintableTextFieldWidget itemField;
    private HintableTextFieldWidget countField;
    private HintableTextFieldWidget posXField, posYField, posZField;
    private HintableTextFieldWidget motionXField, motionYField, motionZField;
    private PonderButton pickBtnPos;
    @Nullable
    private PonderButton jeiBtn;

    public CreateItemEntityScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.create_item_entity.add"), scene, sceneIndex, parent);
    }

    public CreateItemEntityScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                  int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.create_item_entity.edit"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override
    protected int getFormRowCount() { return 5; }

    @Override
    protected String getHeaderTitle() { return UIText.of("ponderer.ui.create_item_entity"); }

    @Override
    protected void buildForm() {
        beginForm();
        var jei = addFormTextFieldWithJei("ponderer.ui.create_item_entity.item", "ponderer.ui.create_item_entity.item.tooltip",
                UIText.of("ponderer.ui.create_item_entity.hint"), IdFieldMode.ITEM);
        itemField = jei.field(); jeiBtn = jei.jeiBtn();
        countField = addFormNumberField("ponderer.ui.create_item_entity.count", "ponderer.ui.create_item_entity.count.tooltip", "1", 50);
        var pos = addFormXyzRow("ponderer.ui.create_item_entity.pos", "ponderer.ui.create_item_entity.pos.tooltip", PickState.TargetField.POS1, true);
        posXField = pos.x(); posYField = pos.y(); posZField = pos.z(); pickBtnPos = pos.pickBtn();
        var motion = addFormXyzRow("ponderer.ui.create_item_entity.motion", "ponderer.ui.create_item_entity.motion.tooltip");
        motionXField = motion.x(); motionYField = motion.y(); motionZField = motion.z();
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.item != null) itemField.setValue(step.item);
        if (step.count != null) countField.setValue(String.valueOf(step.count));
        if (step.pos != null && step.pos.size() >= 3) {
            posXField.setValue(String.valueOf(step.pos.get(0)));
            posYField.setValue(String.valueOf(step.pos.get(1)));
            posZField.setValue(String.valueOf(step.pos.get(2)));
        }
        if (step.motion != null && step.motion.size() >= 3) {
            motionXField.setValue(String.valueOf(step.motion.get(0)));
            motionYField.setValue(String.valueOf(step.motion.get(1)));
            motionZField.setValue(String.valueOf(step.motion.get(2)));
        }
    }

    @Override
    protected String getStepType() { return "create_item_entity"; }

    @Override
    protected Map<String, String> snapshotForm() {
        Map<String, String> m = new HashMap<>();
        m.put("item", itemField.getValue());
        m.put("count", countField.getValue());
        m.put("posX", posXField.getValue());
        m.put("posY", posYField.getValue());
        m.put("posZ", posZField.getValue());
        m.put("motionX", motionXField.getValue());
        m.put("motionY", motionYField.getValue());
        m.put("motionZ", motionZField.getValue());
        return m;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        if (snapshot.containsKey("item")) itemField.setValue(snapshot.get("item"));
        if (snapshot.containsKey("count")) countField.setValue(snapshot.get("count"));
        if (snapshot.containsKey("posX")) posXField.setValue(snapshot.get("posX"));
        if (snapshot.containsKey("posY")) posYField.setValue(snapshot.get("posY"));
        if (snapshot.containsKey("posZ")) posZField.setValue(snapshot.get("posZ"));
        if (snapshot.containsKey("motionX")) motionXField.setValue(snapshot.get("motionX"));
        if (snapshot.containsKey("motionY")) motionYField.setValue(snapshot.get("motionY"));
        if (snapshot.containsKey("motionZ")) motionZField.setValue(snapshot.get("motionZ"));
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;
        String itemId = itemField.getValue().trim();
        if (itemId.isEmpty()) {
            errorMessage = UIText.of("ponderer.ui.create_item_entity.error.required");
            return null;
        }

        ResourceLocation itemLoc = ResourceLocation.tryParse(itemId);
        if (itemLoc == null) {
            errorMessage = UIText.of("ponderer.ui.create_item_entity.error.invalid_id");
            return null;
        }
        if (BuiltInRegistries.ITEM.getOptional(itemLoc).isEmpty()) {
            errorMessage = UIText.of("ponderer.ui.create_item_entity.error.unknown", itemId);
            return null;
        }

        Double px = parseDouble(posXField.getValue(), "X");
        Double py = parseDouble(posYField.getValue(), "Y");
        Double pz = parseDouble(posZField.getValue(), "Z");
        if (px == null || py == null || pz == null) return null;

        Double mx = motionXField.getValue().trim().isEmpty() ? 0.0 : parseDouble(motionXField.getValue(), UIText.of("ponderer.ui.create_item_entity.motion") + " X");
        Double my = motionYField.getValue().trim().isEmpty() ? 0.0 : parseDouble(motionYField.getValue(), UIText.of("ponderer.ui.create_item_entity.motion") + " Y");
        Double mz = motionZField.getValue().trim().isEmpty() ? 0.0 : parseDouble(motionZField.getValue(), UIText.of("ponderer.ui.create_item_entity.motion") + " Z");
        if (mx == null || my == null || mz == null) return null;

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "create_item_entity";
        s.item = itemId;
        s.count = Math.max(1, parseIntOr(countField.getValue(), 1));
        s.pos = List.of(px, py, pz);
        s.motion = List.of(mx, my, mz);
        return s;
    }
}
