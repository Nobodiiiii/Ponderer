package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class CreateItemEntityScreen extends AbstractStepEditorScreen {

    private final StepTextFieldHandle itemField = new StepTextFieldHandle("item");
    private final StepTextFieldHandle countField = new StepTextFieldHandle("count");
    private final StepXyzFieldHandle posField = new StepXyzFieldHandle("pos");
    private final StepXyzFieldHandle motionField = new StepXyzFieldHandle("motion");
    private final StepTextFieldHandle nbtField = new StepTextFieldHandle("nbt");

    public CreateItemEntityScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.create_item_entity.add"), scene, sceneIndex, parent);
    }

    public CreateItemEntityScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                  int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.create_item_entity.edit"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override
    protected String getHeaderTitle() { return UIText.of("ponderer.ui.create_item_entity"); }

    @Override
    protected void collectStepEntries(List<com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.text(
            itemField,
            "ponderer.ui.create_item_entity.item",
            "ponderer.ui.create_item_entity.item.tooltip",
            UIText.of("ponderer.ui.create_item_entity.hint"),
            124,
            FieldDecorators.jei(IdFieldMode.ITEM),
            FieldDecorators.heldItem(
            stack -> {
                itemField.setValue(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                if (stack.getTag() != null && !stack.getTag().isEmpty()) {
                    nbtField.setValue(stack.getTag().toString());
                }
            })));
        entries.add(FieldSpecs.number(
            countField,
            "ponderer.ui.create_item_entity.count",
            "ponderer.ui.create_item_entity.count.tooltip",
            "1",
            50,
            null));
        entries.add(FieldSpecs.xyz(
            posField,
            "ponderer.ui.create_item_entity.pos",
            "ponderer.ui.create_item_entity.pos.tooltip",
            PickState.TargetField.POS1,
            true));
        entries.add(FieldSpecs.xyz(
            motionField,
            "ponderer.ui.create_item_entity.motion",
            "ponderer.ui.create_item_entity.motion.tooltip"));
        entries.add(FieldSpecs.text(
            nbtField,
            "ponderer.ui.create_item_entity.nbt",
            "ponderer.ui.create_item_entity.nbt.tooltip",
            "{PickupDelay:40s}",
            124,
            FieldDecorators.nbtPick("nbt"),
            FieldDecorators.nbtExpand("nbt")));
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.item != null) itemField.setValue(step.item);
        if (step.count != null) countField.setValue(String.valueOf(step.count));
        if (step.pos != null && step.pos.size() >= 3) {
            posField.setValue(step.pos.get(0), step.pos.get(1), step.pos.get(2));
        }
        if (step.motion != null && step.motion.size() >= 3) {
            motionField.setValue(step.motion.get(0), step.motion.get(1), step.motion.get(2));
        }
        if (step.nbt != null) nbtField.setValue(step.nbt);
    }

    @Override
    protected String getStepType() { return "create_item_entity"; }

    @Override
    protected void restoreCustomSnapshot(Map<String, String> snapshot) {
        restoreNbtPickNotice(snapshot);
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        clearStatusMessages();
        String itemId = itemField.getValue().trim();
        if (itemId.isEmpty()) {
            setErrorMessage(UIText.of("ponderer.ui.create_item_entity.error.required"));
            return null;
        }

        ResourceLocation itemLoc = ResourceLocation.tryParse(itemId);
        if (itemLoc == null) {
            setErrorMessage(UIText.of("ponderer.ui.create_item_entity.error.invalid_id"));
            return null;
        }
        if (BuiltInRegistries.ITEM.getOptional(itemLoc).isEmpty()) {
            setErrorMessage(UIText.of("ponderer.ui.create_item_entity.error.unknown", itemId));
            return null;
        }

        Double px = parseDouble(posField.x(), "X");
        Double py = parseDouble(posField.y(), "Y");
        Double pz = parseDouble(posField.z(), "Z");
        if (px == null || py == null || pz == null) return null;

        Double mx = motionField.x().trim().isEmpty() ? 0.0 : parseDouble(motionField.x(), UIText.of("ponderer.ui.create_item_entity.motion") + " X");
        Double my = motionField.y().trim().isEmpty() ? 0.0 : parseDouble(motionField.y(), UIText.of("ponderer.ui.create_item_entity.motion") + " Y");
        Double mz = motionField.z().trim().isEmpty() ? 0.0 : parseDouble(motionField.z(), UIText.of("ponderer.ui.create_item_entity.motion") + " Z");
        if (mx == null || my == null || mz == null) return null;

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "create_item_entity";
        s.item = itemId;
        s.count = Math.max(1, parseIntOr(countField.getValue(), 1));
        s.pos = List.of(px, py, pz);
        s.motion = List.of(mx, my, mz);
        String nbt = nbtField.getValue().trim();
        if (!nbt.isEmpty()) {
            try {
                TagParser.parseTag(nbt);
            } catch (Exception e) {
                setErrorMessage(UIText.of("ponderer.ui.modify_block_entity_nbt.error.invalid"));
                return null;
            }
            s.nbt = nbt;
        }
        return s;
    }
}
