package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreateEntityScreen extends AbstractStepEditorScreen {

    private HintableTextFieldWidget entityField;
    private HintableTextFieldWidget posXField, posYField, posZField;
    private boolean useYawPitch = false;
    private BoxWidget orientModeButton;
    private HintableTextFieldWidget lookAtXField, lookAtYField, lookAtZField;
    private HintableTextFieldWidget yawField, pitchField;
    private HintableTextFieldWidget nbtField;
    private PonderButton pickBtnPos, pickBtnLookAt;
    @Nullable
    private PonderButton jeiBtn;

    public CreateEntityScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.create_entity.add"), scene, sceneIndex, parent);
    }

    public CreateEntityScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                              int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.create_entity.edit"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override
    protected int getFormRowCount() { return 5; }
    @Override
    protected String getHeaderTitle() { return UIText.of("ponderer.ui.create_entity"); }

    @Override
    protected void buildForm() {
        beginForm();
        // Row 1: entity + JEI + world-pick
        var ent = addFormTextFieldWithJeiAndNbtPick("ponderer.ui.create_entity", "ponderer.ui.create_entity.tooltip",
            UIText.of("ponderer.ui.create_entity.hint"), IdFieldMode.ENTITY, "nbt");
        entityField = ent.field();
        jeiBtn = ent.jeiBtn();
        // Row 2: position XYZ + pick
        var pos = addFormXyzRow("ponderer.ui.create_entity.pos", "ponderer.ui.create_entity.pos.tooltip",
                PickState.TargetField.POS1, true);
        posXField = pos.x(); posYField = pos.y(); posZField = pos.z(); pickBtnPos = pos.pickBtn();
        // Row 3: orient mode cycle
        orientModeButton = addFormCycleButton("ponderer.ui.create_entity.orient", "ponderer.ui.create_entity.orient.tooltip",
                100, () -> { useYawPitch = !useYawPitch; updateOrientVis(); },
                () -> useYawPitch ? UIText.of("ponderer.ui.create_entity.yaw_pitch") : UIText.of("ponderer.ui.create_entity.lookat"));
        // Row 4: conditional - lookAt XYZ or yaw/pitch (manual layout)
        int sw = 38;
        lookAtXField = createSmallNumberField(fieldX(), formY(), sw, "X");
        lookAtYField = createSmallNumberField(fieldX() + sw + 5, formY(), sw, "Y");
        lookAtZField = createSmallNumberField(fieldX() + 2 * (sw + 5), formY(), sw, "Z");
        pickBtnLookAt = createPickButton(fieldX() + 3 * (sw + 5), formY(), PickState.TargetField.LOOK_AT, true);
        yawField = createSmallNumberField(fieldX(), formY(), sw + 15, "0.0");
        pitchField = createSmallNumberField(fieldX() + sw + 20, formY(), sw + 15, "0.0");
        nextFormRow();

        nbtField = addFormNbtField("ponderer.ui.create_entity.nbt", "ponderer.ui.create_entity.nbt.tooltip",
            "{NoAI:1b}", 124, "nbt");

        updateOrientVis();
    }

    private void updateOrientVis() {
        setWidgetVisible(lookAtXField, !useYawPitch);
        setWidgetVisible(lookAtYField, !useYawPitch);
        setWidgetVisible(lookAtZField, !useYawPitch);
        setWidgetVisible(pickBtnLookAt, !useYawPitch);
        setWidgetVisible(yawField, useYawPitch);
        setWidgetVisible(pitchField, useYawPitch);
    }

    private static void setWidgetVisible(net.minecraft.client.gui.components.AbstractWidget widget, boolean visible) {
        widget.visible = visible;
        widget.active = visible;
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.entity != null) entityField.setValue(step.entity);
        if (step.pos != null && step.pos.size() >= 3) {
            posXField.setValue(String.valueOf(step.pos.get(0)));
            posYField.setValue(String.valueOf(step.pos.get(1)));
            posZField.setValue(String.valueOf(step.pos.get(2)));
        }
        if (step.yaw != null || step.pitch != null) {
            useYawPitch = true;
            if (step.yaw != null) yawField.setValue(String.valueOf(step.yaw));
            if (step.pitch != null) pitchField.setValue(String.valueOf(step.pitch));
        } else if (step.lookAt != null && step.lookAt.size() >= 3) {
            lookAtXField.setValue(String.valueOf(step.lookAt.get(0)));
            lookAtYField.setValue(String.valueOf(step.lookAt.get(1)));
            lookAtZField.setValue(String.valueOf(step.lookAt.get(2)));
        }
        if (step.nbt != null) nbtField.setValue(step.nbt);
        updateOrientVis();
    }

    @Override
    protected void renderForm(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        updateOrientVis();
        super.renderForm(graphics, mouseX, mouseY, partialTicks);
        // Row 4: dynamic label based on orient mode
        var font = Minecraft.getInstance().font;
        int lx = guiLeft + 10;
        int y = guiTop + FORM_TOP + 3 * ROW_HEIGHT + 3;
        graphics.drawString(font,
                useYawPitch ? UIText.of("ponderer.ui.create_entity.yaw_pitch") : UIText.of("ponderer.ui.create_entity.lookat"),
                lx, y, UILayoutConstants.COLOR_LABEL);
    }

    @Override
    protected void renderFormForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        updateOrientVis();
        super.renderFormForeground(graphics, mouseX, mouseY, partialTicks);
        // Row 4: pick button for lookAt mode
        if (!useYawPitch) renderPickButtonLabel(graphics, pickBtnLookAt);
    }

    @Override
    protected String getStepType() { return "create_entity"; }

    @Override
    protected Map<String, String> snapshotForm() {
        Map<String, String> m = new HashMap<>();
        m.put("entity", entityField.getValue());
        m.put("posX", posXField.getValue());
        m.put("posY", posYField.getValue());
        m.put("posZ", posZField.getValue());
        m.put("useYawPitch", String.valueOf(useYawPitch));
        m.put("lookAtX", lookAtXField.getValue());
        m.put("lookAtY", lookAtYField.getValue());
        m.put("lookAtZ", lookAtZField.getValue());
        m.put("yaw", yawField.getValue());
        m.put("pitch", pitchField.getValue());
        m.put("nbt", nbtField.getValue());
        return m;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        if (snapshot.containsKey(NbtPickState.SNAPSHOT_ENTITY_ID_KEY)) {
            entityField.setValue(snapshot.get(NbtPickState.SNAPSHOT_ENTITY_ID_KEY));
        } else if (snapshot.containsKey("entity")) {
            entityField.setValue(snapshot.get("entity"));
        }
        if (snapshot.containsKey("posX")) posXField.setValue(snapshot.get("posX"));
        if (snapshot.containsKey("posY")) posYField.setValue(snapshot.get("posY"));
        if (snapshot.containsKey("posZ")) posZField.setValue(snapshot.get("posZ"));
        if (snapshot.containsKey("useYawPitch")) useYawPitch = Boolean.parseBoolean(snapshot.get("useYawPitch"));
        if (snapshot.containsKey("lookAtX")) lookAtXField.setValue(snapshot.get("lookAtX"));
        if (snapshot.containsKey("lookAtY")) lookAtYField.setValue(snapshot.get("lookAtY"));
        if (snapshot.containsKey("lookAtZ")) lookAtZField.setValue(snapshot.get("lookAtZ"));
        if (snapshot.containsKey("yaw")) yawField.setValue(snapshot.get("yaw"));
        if (snapshot.containsKey("pitch")) pitchField.setValue(snapshot.get("pitch"));
        if (snapshot.containsKey("nbt")) nbtField.setValue(snapshot.get("nbt"));
        restoreNbtPickNotice(snapshot);
        updateOrientVis();
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;
        String entityId = entityField.getValue().trim();
        if (entityId.isEmpty()) { errorMessage = UIText.of("ponderer.ui.create_entity.error.required"); return null; }
        ResourceLocation loc = ResourceLocation.tryParse(entityId);
        if (loc == null) { errorMessage = UIText.of("ponderer.ui.create_entity.error.invalid_id"); return null; }
        if (BuiltInRegistries.ENTITY_TYPE.getOptional(loc).isEmpty()) {
            errorMessage = UIText.of("ponderer.ui.create_entity.error.unknown", entityId); return null;
        }
        Double px = parseDouble(posXField.getValue(), "X");
        Double py = parseDouble(posYField.getValue(), "Y");
        Double pz = parseDouble(posZField.getValue(), "Z");
        if (px == null || py == null || pz == null) return null;

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "create_entity";
        s.entity = entityId;
        s.pos = List.of(px, py, pz);
        if (useYawPitch) {
            s.yaw = (float) parseDoubleOr(yawField.getValue(), 0);
            s.pitch = (float) parseDoubleOr(pitchField.getValue(), 0);
        } else {
            Double lx2 = parseDouble(lookAtXField.getValue(), "X");
            Double ly2 = parseDouble(lookAtYField.getValue(), "Y");
            Double lz2 = parseDouble(lookAtZField.getValue(), "Z");
            if (lx2 != null && ly2 != null && lz2 != null) s.lookAt = List.of(lx2, ly2, lz2);
        }
        String nbt = nbtField.getValue().trim();
        if (!nbt.isEmpty()) {
            try {
                TagParser.parseTag(nbt);
            } catch (Exception e) {
                errorMessage = UIText.of("ponderer.ui.modify_block_entity_nbt.error.invalid");
                return null;
            }
            s.nbt = nbt;
        }
        return s;
    }
}
