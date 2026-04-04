package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class ReplaceBlocksScreen extends AbstractStepEditorScreen {

    private final StepTextFieldHandle blockField = new StepTextFieldHandle("block");
    private final StepXyzFieldHandle posField = new StepXyzFieldHandle("pos");
    private final StepXyzFieldHandle pos2Field = new StepXyzFieldHandle("pos2");
    private boolean spawnParticles = true;

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
    protected String getHeaderTitle() { return UIText.of("ponderer.ui.replace_blocks"); }

    @Override
    protected void collectStepEntries(List<com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry> entries) {
        entries.add(StepEditorEntries.text(
            blockField,
            "ponderer.ui.replace_blocks",
            "ponderer.ui.replace_blocks.tooltip",
            UIText.of("ponderer.ui.replace_blocks.hint"),
            124,
            StepTextButtonSpec.jei(IdFieldMode.BLOCK)));
        entries.add(StepEditorEntries.blockProps(
            "ponderer.ui.block_properties",
            "ponderer.ui.block_properties.tooltip",
            this::blockPropRowCount));
        entries.add(StepEditorEntries.xyz(
            posField,
            "ponderer.ui.replace_blocks.pos_from",
            "ponderer.ui.replace_blocks.pos_from.tooltip",
            PickState.TargetField.POS1));
        entries.add(StepEditorEntries.xyz(
            pos2Field,
            "ponderer.ui.replace_blocks.pos_to",
            "ponderer.ui.replace_blocks.pos_to.tooltip",
            PickState.TargetField.POS2));
        entries.add(StepEditorEntries.toggle(
            "ponderer.ui.replace_blocks.particles",
            "ponderer.ui.replace_blocks.particles.tooltip",
            () -> spawnParticles,
            () -> spawnParticles = !spawnParticles));
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.block != null) blockField.setValue(step.block);
        if (step.blockPos != null && step.blockPos.size() >= 3) {
            posField.setValue(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
        }
        if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
            pos2Field.setValue(step.blockPos2.get(0), step.blockPos2.get(1), step.blockPos2.get(2));
        }
        if (step.spawnParticles != null) {
            spawnParticles = step.spawnParticles;
        }
    }



    @Override
    protected String getStepType() { return "replace_blocks"; }

    @Override
    protected void appendCustomSnapshot(Map<String, String> m) {
        syncBlockPropFieldsToEntries();
        snapshotBlockProps(m);
        m.put("particles", String.valueOf(spawnParticles));
    }

    @Override
    protected void restoreCustomSnapshot(Map<String, String> snapshot) {
        restoreBlockProps(snapshot);
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
