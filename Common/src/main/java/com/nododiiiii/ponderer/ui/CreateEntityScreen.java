package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class CreateEntityScreen extends AbstractStepEditorScreen {

    private final StepTextFieldHandle entityField = new StepTextFieldHandle("entity");
    private final StepXyzFieldHandle posField = new StepXyzFieldHandle("pos");
    private boolean useYawPitch = false;
    private final StepXyzFieldHandle lookAtField = new StepXyzFieldHandle("lookAt");
    private final StepTextFieldHandle yawField = new StepTextFieldHandle("yaw");
    private final StepTextFieldHandle pitchField = new StepTextFieldHandle("pitch");
    private final StepTextFieldHandle nbtField = new StepTextFieldHandle("nbt");

    public CreateEntityScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.create_entity.add"), scene, sceneIndex, parent);
    }

    public CreateEntityScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                              int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.create_entity.edit"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override
    protected String getHeaderTitle() { return UIText.of("ponderer.ui.create_entity"); }

    @Override
    protected void collectStepEntries(List<com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.text(
            entityField,
            "ponderer.ui.create_entity",
            "ponderer.ui.create_entity.tooltip",
            UIText.of("ponderer.ui.create_entity.hint"),
            124,
            FieldDecorators.jei(IdFieldMode.ENTITY),
            FieldDecorators.nbtPick("nbt")));
        entries.add(FieldSpecs.xyz(
            posField,
            "ponderer.ui.create_entity.pos",
            "ponderer.ui.create_entity.pos.tooltip",
            PickState.TargetField.POS1,
            true));
        entries.add(FieldSpecs.choice(
            "ponderer.ui.create_entity.orient",
            "ponderer.ui.create_entity.orient.tooltip",
            100,
            () -> {
                useYawPitch = !useYawPitch;
                rebuildFormPreservingState();
            },
            () -> useYawPitch ? UIText.of("ponderer.ui.create_entity.yaw_pitch") : UIText.of("ponderer.ui.create_entity.lookat")));
        if (useYawPitch) {
            entries.add(FieldSpecs.dualText(
                yawField,
                pitchField,
                "ponderer.ui.create_entity.yaw_pitch",
                null,
                "0.0",
                53,
                "0.0",
                53));
        } else {
            entries.add(FieldSpecs.xyz(
                lookAtField,
                "ponderer.ui.create_entity.lookat",
                null,
                PickState.TargetField.LOOK_AT,
                true));
        }
        entries.add(FieldSpecs.text(
            nbtField,
            "ponderer.ui.create_entity.nbt",
            "ponderer.ui.create_entity.nbt.tooltip",
            "{NoAI:1b}",
            124,
            FieldDecorators.nbtPick("nbt"),
            FieldDecorators.nbtExpand("nbt")));
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.entity != null) entityField.setValue(step.entity);
        if (step.pos != null && step.pos.size() >= 3) {
            posField.setValue(step.pos.get(0), step.pos.get(1), step.pos.get(2));
        }
        if (step.yaw != null || step.pitch != null) {
            useYawPitch = true;
            if (step.yaw != null) yawField.setValue(String.valueOf(step.yaw));
            if (step.pitch != null) pitchField.setValue(String.valueOf(step.pitch));
        } else if (step.lookAt != null && step.lookAt.size() >= 3) {
            lookAtField.setValue(step.lookAt.get(0), step.lookAt.get(1), step.lookAt.get(2));
        }
        if (step.nbt != null) nbtField.setValue(step.nbt);
    }

    @Override
    protected String getStepType() { return "create_entity"; }

    @Override
    protected void appendCustomSnapshot(Map<String, String> snapshot) {
        snapshot.put("useYawPitch", String.valueOf(useYawPitch));
    }

    @Override
    protected void restoreCustomSnapshot(Map<String, String> snapshot) {
        if (snapshot.containsKey(NbtPickState.SNAPSHOT_ENTITY_ID_KEY)) {
            entityField.setValue(snapshot.get(NbtPickState.SNAPSHOT_ENTITY_ID_KEY));
        }
        if (snapshot.containsKey("useYawPitch")) useYawPitch = Boolean.parseBoolean(snapshot.get("useYawPitch"));
        restoreNbtPickNotice(snapshot);
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        clearStatusMessages();
        String entityId = entityField.getValue().trim();
        if (entityId.isEmpty()) { setErrorMessage(UIText.of("ponderer.ui.create_entity.error.required")); return null; }
        ResourceLocation loc = ResourceLocation.tryParse(entityId);
        if (loc == null) { setErrorMessage(UIText.of("ponderer.ui.create_entity.error.invalid_id")); return null; }
        if (BuiltInRegistries.ENTITY_TYPE.getOptional(loc).isEmpty()) {
            setErrorMessage(UIText.of("ponderer.ui.create_entity.error.unknown", entityId)); return null;
        }
        Double px = parseDouble(posField.x(), "X");
        Double py = parseDouble(posField.y(), "Y");
        Double pz = parseDouble(posField.z(), "Z");
        if (px == null || py == null || pz == null) return null;

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "create_entity";
        s.entity = entityId;
        s.pos = List.of(px, py, pz);
        if (useYawPitch) {
            s.yaw = (float) parseDoubleOr(yawField.getValue(), 0);
            s.pitch = (float) parseDoubleOr(pitchField.getValue(), 0);
        } else {
            Double lx2 = parseDouble(lookAtField.x(), "X");
            Double ly2 = parseDouble(lookAtField.y(), "Y");
            Double lz2 = parseDouble(lookAtField.z(), "Z");
            if (lx2 != null && ly2 != null && lz2 != null) s.lookAt = List.of(lx2, ly2, lz2);
        }
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
