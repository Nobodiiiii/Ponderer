package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReplaceBlocksScreen extends AbstractStepEditorScreen {

    private HintableTextFieldWidget blockField;
    private HintableTextFieldWidget posXField, posYField, posZField;
    private HintableTextFieldWidget pos2XField, pos2YField, pos2ZField;
    private boolean spawnParticles = true;
    private BoxWidget particlesToggle;
    private PonderButton pickBtn1, pickBtn2;
    @Nullable
    private PonderButton jeiBtn;

    public ReplaceBlocksScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.replace_blocks.add"), scene, sceneIndex, parent);
    }

    public ReplaceBlocksScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                               int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.replace_blocks.edit"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override
    protected boolean usesBlockProps() { return true; }

    @Override
    protected int getFormRowCount() { return 4 + blockPropRowCount(); }

    @Override
    protected String getHeaderTitle() { return UIText.of("ponderer.ui.replace_blocks"); }

    @Override
    protected void buildForm() {
        beginForm();
        var blk = addFormTextFieldWithJei("ponderer.ui.replace_blocks", "ponderer.ui.replace_blocks.tooltip",
                UIText.of("ponderer.ui.replace_blocks.hint"), IdFieldMode.BLOCK);
        blockField = blk.field();
        jeiBtn = blk.jeiBtn();
        addFormBlockProps("ponderer.ui.block_properties", "ponderer.ui.block_properties.tooltip");
        var from = addFormXyzRow("ponderer.ui.replace_blocks.pos_from", "ponderer.ui.replace_blocks.pos_from.tooltip", PickState.TargetField.POS1);
        posXField = from.x(); posYField = from.y(); posZField = from.z(); pickBtn1 = from.pickBtn();
        var to = addFormXyzRow("ponderer.ui.replace_blocks.pos_to", "ponderer.ui.replace_blocks.pos_to.tooltip", PickState.TargetField.POS2);
        pos2XField = to.x(); pos2YField = to.y(); pos2ZField = to.z(); pickBtn2 = to.pickBtn();
        particlesToggle = addFormToggle("ponderer.ui.replace_blocks.particles", "ponderer.ui.replace_blocks.particles.tooltip",
                () -> spawnParticles, () -> spawnParticles = !spawnParticles);
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.block != null) blockField.setValue(step.block);
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
        if (step.spawnParticles != null) {
            spawnParticles = step.spawnParticles;
        }
    }



    @Override
    protected String getStepType() { return "replace_blocks"; }

    @Override
    protected Map<String, String> snapshotForm() {
        syncBlockPropFieldsToEntries();
        Map<String, String> m = new HashMap<>();
        m.put("block", blockField.getValue());
        snapshotBlockProps(m);
        m.put("posX", posXField.getValue());
        m.put("posY", posYField.getValue());
        m.put("posZ", posZField.getValue());
        m.put("pos2X", pos2XField.getValue());
        m.put("pos2Y", pos2YField.getValue());
        m.put("pos2Z", pos2ZField.getValue());
        m.put("particles", String.valueOf(spawnParticles));
        return m;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        if (snapshot.containsKey("block")) blockField.setValue(snapshot.get("block"));
        restoreBlockProps(snapshot);
        if (snapshot.containsKey("posX")) posXField.setValue(snapshot.get("posX"));
        if (snapshot.containsKey("posY")) posYField.setValue(snapshot.get("posY"));
        if (snapshot.containsKey("posZ")) posZField.setValue(snapshot.get("posZ"));
        if (snapshot.containsKey("pos2X")) pos2XField.setValue(snapshot.get("pos2X"));
        if (snapshot.containsKey("pos2Y")) pos2YField.setValue(snapshot.get("pos2Y"));
        if (snapshot.containsKey("pos2Z")) pos2ZField.setValue(snapshot.get("pos2Z"));
        if (snapshot.containsKey("particles")) spawnParticles = Boolean.parseBoolean(snapshot.get("particles"));
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;
        String blockId = blockField.getValue().trim();
        if (blockId.isEmpty()) { errorMessage = UIText.of("ponderer.ui.replace_blocks.error.required"); return null; }
        ResourceLocation loc = ResourceLocation.tryParse(blockId);
        if (loc == null) { errorMessage = UIText.of("ponderer.ui.replace_blocks.error.invalid_id"); return null; }
        if (BuiltInRegistries.BLOCK.getOptional(loc).isEmpty()) {
            errorMessage = UIText.of("ponderer.ui.replace_blocks.error.unknown", blockId); return null;
        }

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
                errorMessage = UIText.of("ponderer.ui.replace_blocks.error.partial_to");
                return null;
            }
            px2 = parseInt(pos2X, "X2");
            py2 = parseInt(pos2Y, "Y2");
            pz2 = parseInt(pos2Z, "Z2");
            if (px2 == null || py2 == null || pz2 == null) return null;
        }

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "replace_blocks";
        s.block = blockId;
        s.blockProperties = collectBlockProperties();
        s.blockPos = List.of(px, py, pz);
        if (hasPos2) s.blockPos2 = List.of(px2, py2, pz2);
        if (!spawnParticles) s.spawnParticles = false;
        return s;
    }
}
