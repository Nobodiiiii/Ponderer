package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Shared editor for modify_entities_nbt and modify_item_entities_nbt steps.
 */
public class ModifyEntitiesNbtScreen extends AbstractStepEditorScreen {

    private final String stepType;
    private final IdFieldMode jeiMode;

    private final StepTextFieldHandle idField = new StepTextFieldHandle("id");
    private final StepXyzFieldHandle posField = new StepXyzFieldHandle("pos");
    private final StepXyzFieldHandle pos2Field = new StepXyzFieldHandle("pos2");
    private final StepTextFieldHandle nbtField = new StepTextFieldHandle("nbt");
    private boolean fullScene = false;

    public ModifyEntitiesNbtScreen(String stepType, DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui." + stepType + ".add"), scene, sceneIndex, parent);
        this.stepType = stepType;
        this.jeiMode = "modify_item_entities_nbt".equals(stepType) ? IdFieldMode.ITEM : IdFieldMode.ENTITY;
    }

    public ModifyEntitiesNbtScreen(String stepType, DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                   int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui." + stepType + ".edit"), scene, sceneIndex, parent, editIndex, step);
        this.stepType = stepType;
        this.jeiMode = "modify_item_entities_nbt".equals(stepType) ? IdFieldMode.ITEM : IdFieldMode.ENTITY;
    }

    @Override
    protected String getHeaderTitle() { return UIText.of("ponderer.ui." + stepType); }

    @Override
    protected void collectStepEntries(List<com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry> entries) {
        if (jeiMode == IdFieldMode.ITEM) {
            entries.add(FieldSpecs.text(
                idField,
                "ponderer.ui." + stepType + ".id",
                "ponderer.ui." + stepType + ".id.tooltip",
                UIText.of("ponderer.ui." + stepType + ".id.hint"),
                124,
                FieldDecorators.jei(jeiMode),
                FieldDecorators.heldItem(
                stack -> {
                    idField.setValue(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    if (stack.getTag() != null && !stack.getTag().isEmpty()) {
                        nbtField.setValue(stack.getTag().toString());
                    }
                })));
        } else {
            entries.add(FieldSpecs.text(
                idField,
                "ponderer.ui." + stepType + ".id",
                "ponderer.ui." + stepType + ".id.tooltip",
                UIText.of("ponderer.ui." + stepType + ".id.hint"),
                124,
                FieldDecorators.jei(jeiMode)));
        }
        entries.add(FieldSpecs.xyz(
            posField,
            "ponderer.ui." + stepType + ".pos_from",
            "ponderer.ui." + stepType + ".pos_from.tooltip",
            PickState.TargetField.POS1));
        entries.add(FieldSpecs.xyz(
            pos2Field,
            "ponderer.ui." + stepType + ".pos_to",
            "ponderer.ui." + stepType + ".pos_to.tooltip",
            PickState.TargetField.POS2));
        entries.add(FieldSpecs.text(
            nbtField,
            "ponderer.ui." + stepType + ".nbt",
            "ponderer.ui." + stepType + ".nbt.tooltip",
            "{NoGravity:1b}",
            124,
            FieldDecorators.nbtPick("nbt")));
        entries.add(FieldSpecs.toggle(
            "ponderer.ui." + stepType + ".full_scene",
            "ponderer.ui." + stepType + ".full_scene.tooltip",
            () -> fullScene,
            () -> fullScene = !fullScene));
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.item != null) idField.setValue(step.item);
        if (step.entity != null) idField.setValue(step.entity);
        if (step.blockPos != null && step.blockPos.size() >= 3) {
            posField.setValue(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
        }
        if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
            pos2Field.setValue(step.blockPos2.get(0), step.blockPos2.get(1), step.blockPos2.get(2));
        }
        if (step.nbt != null) nbtField.setValue(step.nbt);
        if (step.fullScene != null) fullScene = step.fullScene;
    }

    @Override
    protected String getStepType() { return stepType; }

    @Override
    protected void appendCustomSnapshot(Map<String, String> snapshot) {
        snapshot.put("fullScene", String.valueOf(fullScene));
    }

    @Override
    protected void restoreCustomSnapshot(Map<String, String> snapshot) {
        if (snapshot.containsKey("fullScene")) {
            fullScene = Boolean.parseBoolean(snapshot.get("fullScene"));
        }
        restoreNbtPickNotice(snapshot);
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;

        String nbt = nbtField.getValue().trim();
        if (nbt.isEmpty()) {
            errorMessage = UIText.of("ponderer.ui.modify_block_entity_nbt.error.required");
            return null;
        }
        try {
            TagParser.parseTag(nbt);
        } catch (Exception e) {
            errorMessage = UIText.of("ponderer.ui.modify_block_entity_nbt.error.invalid");
            return null;
        }

        Integer px = null, py = null, pz = null;
        Integer px2 = null, py2 = null, pz2 = null;

        if (!fullScene) {
            px = parseInt(posField.x(), "X");
            py = parseInt(posField.y(), "Y");
            pz = parseInt(posField.z(), "Z");
            if (px == null || py == null || pz == null) return null;

            String pos2X = pos2Field.x().trim();
            String pos2Y = pos2Field.y().trim();
            String pos2Z = pos2Field.z().trim();
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
        s.nbt = nbt;

        String id = idField.getValue().trim();
        if (!id.isEmpty()) {
            if ("modify_item_entities_nbt".equals(stepType)) {
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
