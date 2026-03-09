package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModifyBlockEntityNbtScreen extends AbstractStepEditorScreen {

    private HintableTextFieldWidget posXField, posYField, posZField;
    private HintableTextFieldWidget pos2XField, pos2YField, pos2ZField;
    private HintableTextFieldWidget nbtField;
    private boolean redraw = false;
    private BoxWidget redrawToggle;
    private PonderButton pickBtn1, pickBtn2;

    public ModifyBlockEntityNbtScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.modify_block_entity_nbt.add"), scene, sceneIndex, parent);
    }

    public ModifyBlockEntityNbtScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                      int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.modify_block_entity_nbt.edit"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override
    protected boolean usesBlockProps() { return true; }

    @Override
    protected int getFormRowCount() { return 4 + blockPropRowCount(); }

    @Override
    protected String getHeaderTitle() { return UIText.of("ponderer.ui.modify_block_entity_nbt"); }

    @Override
    protected void buildForm() {
        beginForm();
        var pos1 = addFormXyzRow("ponderer.ui.modify_block_entity_nbt.pos_from", "ponderer.ui.modify_block_entity_nbt.pos_from.tooltip", PickState.TargetField.POS1);
        posXField = pos1.x(); posYField = pos1.y(); posZField = pos1.z(); pickBtn1 = pos1.pickBtn();
        var pos2 = addFormXyzRow("ponderer.ui.modify_block_entity_nbt.pos_to", "ponderer.ui.modify_block_entity_nbt.pos_to.tooltip", PickState.TargetField.POS2);
        pos2XField = pos2.x(); pos2YField = pos2.y(); pos2ZField = pos2.z(); pickBtn2 = pos2.pickBtn();
        addFormBlockProps("ponderer.ui.modify_block_entity_nbt.properties", "ponderer.ui.modify_block_entity_nbt.properties.tooltip");
        nbtField = addFormNbtField("ponderer.ui.modify_block_entity_nbt.nbt", "ponderer.ui.modify_block_entity_nbt.nbt.tooltip",
                "{CustomName:'\"Demo\"'}", 124, "nbt");
        redrawToggle = addFormToggle("ponderer.ui.modify_block_entity_nbt.redraw", "ponderer.ui.modify_block_entity_nbt.redraw.tooltip",
                () -> redraw, () -> redraw = !redraw);
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
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
        if (step.nbt != null) nbtField.setValue(step.nbt);
        if (step.reDrawBlocks != null) redraw = step.reDrawBlocks;
    }

    @Override
    protected String getStepType() { return "modify_block_entity_nbt"; }

    @Override
    protected Map<String, String> snapshotForm() {
        syncBlockPropFieldsToEntries();
        Map<String, String> m = new HashMap<>();
        m.put("posX", posXField.getValue());
        m.put("posY", posYField.getValue());
        m.put("posZ", posZField.getValue());
        m.put("pos2X", pos2XField.getValue());
        m.put("pos2Y", pos2YField.getValue());
        m.put("pos2Z", pos2ZField.getValue());
        snapshotBlockProps(m);
        m.put("nbt", nbtField.getValue());
        m.put("redraw", String.valueOf(redraw));
        return m;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        if (snapshot.containsKey("posX")) posXField.setValue(snapshot.get("posX"));
        if (snapshot.containsKey("posY")) posYField.setValue(snapshot.get("posY"));
        if (snapshot.containsKey("posZ")) posZField.setValue(snapshot.get("posZ"));
        if (snapshot.containsKey("pos2X")) pos2XField.setValue(snapshot.get("pos2X"));
        if (snapshot.containsKey("pos2Y")) pos2YField.setValue(snapshot.get("pos2Y"));
        if (snapshot.containsKey("pos2Z")) pos2ZField.setValue(snapshot.get("pos2Z"));
        restoreBlockProps(snapshot);
        if (snapshot.containsKey("nbt")) nbtField.setValue(snapshot.get("nbt"));
        restoreNbtPickNotice(snapshot);
        if (snapshot.containsKey("redraw")) redraw = Boolean.parseBoolean(snapshot.get("redraw"));
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;

        Integer px = parseInt(posXField.getValue(), "X");
        Integer py = parseInt(posYField.getValue(), "Y");
        Integer pz = parseInt(posZField.getValue(), "Z");
        if (px == null || py == null || pz == null) return null;

        String pos2X = pos2XField.getValue().trim();
        String pos2Y = pos2YField.getValue().trim();
        String pos2Z = pos2ZField.getValue().trim();
        boolean hasPos2 = !pos2X.isEmpty() || !pos2Y.isEmpty() || !pos2Z.isEmpty();
        Integer px2 = null, py2 = null, pz2 = null;
        if (hasPos2) {
            if (pos2X.isEmpty() || pos2Y.isEmpty() || pos2Z.isEmpty()) {
                errorMessage = UIText.of("ponderer.ui.modify_block_entity_nbt.error.partial_to");
                return null;
            }
            px2 = parseInt(pos2X, "X2");
            py2 = parseInt(pos2Y, "Y2");
            pz2 = parseInt(pos2Z, "Z2");
            if (px2 == null || py2 == null || pz2 == null) return null;
        }

        String nbt = nbtField.getValue().trim();
        Map<String, String> props = collectBlockProperties();
        if (nbt.isEmpty() && props == null) {
            errorMessage = UIText.of("ponderer.ui.modify_block_entity_nbt.error.required");
            return null;
        }
        if (!nbt.isEmpty()) {
            try {
                TagParser.parseTag(nbt);
            } catch (Exception e) {
                errorMessage = UIText.of("ponderer.ui.modify_block_entity_nbt.error.invalid");
                return null;
            }
        }

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "modify_block_entity_nbt";
        s.blockPos = List.of(px, py, pz);
        if (hasPos2) s.blockPos2 = List.of(px2, py2, pz2);
        s.blockProperties = props;
        if (!nbt.isEmpty()) s.nbt = nbt;
        if (redraw) s.reDrawBlocks = true;
        return s;
    }
}
