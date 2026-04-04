package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class ModifyBlockEntityNbtScreen extends AbstractStepEditorScreen {

    private final StepXyzFieldHandle posField = new StepXyzFieldHandle("pos");
    private final StepXyzFieldHandle pos2Field = new StepXyzFieldHandle("pos2");
    private final StepTextFieldHandle nbtField = new StepTextFieldHandle("nbt");
    private boolean redraw = false;

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
    protected String getHeaderTitle() { return UIText.of("ponderer.ui.modify_block_entity_nbt"); }

    @Override
    protected void collectStepEntries(List<com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry> entries) {
        entries.add(StepEditorEntries.xyz(
            posField,
            "ponderer.ui.modify_block_entity_nbt.pos_from",
            "ponderer.ui.modify_block_entity_nbt.pos_from.tooltip",
            PickState.TargetField.POS1));
        entries.add(StepEditorEntries.xyz(
            pos2Field,
            "ponderer.ui.modify_block_entity_nbt.pos_to",
            "ponderer.ui.modify_block_entity_nbt.pos_to.tooltip",
            PickState.TargetField.POS2));
        entries.add(StepEditorEntries.blockProps(
            "ponderer.ui.modify_block_entity_nbt.properties",
            "ponderer.ui.modify_block_entity_nbt.properties.tooltip",
            this::blockPropRowCount));
        entries.add(StepEditorEntries.text(
            nbtField,
            "ponderer.ui.modify_block_entity_nbt.nbt",
            "ponderer.ui.modify_block_entity_nbt.nbt.tooltip",
            "{CustomName:'\"Demo\"'}",
            124,
            StepTextButtonSpec.nbtPick("nbt")));
        entries.add(StepEditorEntries.toggle(
            "ponderer.ui.modify_block_entity_nbt.redraw",
            "ponderer.ui.modify_block_entity_nbt.redraw.tooltip",
            () -> redraw,
            () -> redraw = !redraw));
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
        if (step.nbt != null) nbtField.setValue(step.nbt);
        if (step.reDrawBlocks != null) redraw = step.reDrawBlocks;
    }

    @Override
    protected String getStepType() { return "modify_block_entity_nbt"; }

    @Override
    protected void appendCustomSnapshot(Map<String, String> m) {
        syncBlockPropFieldsToEntries();
        snapshotBlockProps(m);
        m.put("redraw", String.valueOf(redraw));
    }

    @Override
    protected void restoreCustomSnapshot(Map<String, String> snapshot) {
        restoreBlockProps(snapshot);
        restoreNbtPickNotice(snapshot);
        if (snapshot.containsKey("redraw")) redraw = Boolean.parseBoolean(snapshot.get("redraw"));
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;

        Integer px = parseInt(posField.x(), "X");
        Integer py = parseInt(posField.y(), "Y");
        Integer pz = parseInt(posField.z(), "Z");
        if (px == null || py == null || pz == null) return null;

        String pos2X = pos2Field.x().trim();
        String pos2Y = pos2Field.y().trim();
        String pos2Z = pos2Field.z().trim();
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
