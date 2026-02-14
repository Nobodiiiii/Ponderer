package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

public class CreateEntityScreen extends AbstractStepEditorScreen {

    private HintableTextFieldWidget entityField;
    private HintableTextFieldWidget posXField, posYField, posZField;
    private boolean useYawPitch = false;
    private BoxWidget orientModeButton;
    private HintableTextFieldWidget lookAtXField, lookAtYField, lookAtZField;
    private HintableTextFieldWidget yawField, pitchField;

    public CreateEntityScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.create_entity.add"), scene, sceneIndex, parent);
    }

    public CreateEntityScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                              int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.create_entity.edit"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override
    protected int getFormRowCount() { return 4; }
    @Override
    protected String getHeaderTitle() { return UIText.of("ponderer.ui.create_entity"); }

    @Override
    protected void buildForm() {
        int x = guiLeft + 70, y = guiTop + 26, sw = 40;
        int lx = guiLeft + 10;

        entityField = createTextField(x, y, 140, 18, UIText.of("ponderer.ui.create_entity.hint"));
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui.create_entity"), UIText.of("ponderer.ui.create_entity.tooltip"));
        y += 22;
        posXField = createSmallNumberField(x, y, sw, "X");
        posYField = createSmallNumberField(x + sw + 5, y, sw, "Y");
        posZField = createSmallNumberField(x + 2 * (sw + 5), y, sw, "Z");
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui.create_entity.pos"), UIText.of("ponderer.ui.create_entity.pos.tooltip"));
        y += 22;
        orientModeButton = createFormButton(x, y, 100);
        orientModeButton.withCallback(() -> { useYawPitch = !useYawPitch; updateOrientVis(); });
        addRenderableWidget(orientModeButton);
        addLabelTooltip(lx, y + 1, UIText.of("ponderer.ui.create_entity.orient"), UIText.of("ponderer.ui.create_entity.orient.tooltip"));
        y += 22;
        lookAtXField = createSmallNumberField(x, y, sw, "X");
        lookAtYField = createSmallNumberField(x + sw + 5, y, sw, "Y");
        lookAtZField = createSmallNumberField(x + 2 * (sw + 5), y, sw, "Z");
        yawField = createSmallNumberField(x, y, sw + 15, "0.0");
        pitchField = createSmallNumberField(x + sw + 20, y, sw + 15, "0.0");
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui.create_entity.lookat"), UIText.of("ponderer.ui.create_entity.lookat.tooltip"));

        updateOrientVis();
    }

    private void updateOrientVis() {
        lookAtXField.visible = !useYawPitch;
        lookAtYField.visible = !useYawPitch;
        lookAtZField.visible = !useYawPitch;
        yawField.visible = useYawPitch;
        pitchField.visible = useYawPitch;
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
        updateOrientVis();
    }

    @Override
    protected void renderForm(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        int lx = guiLeft + 10, y = guiTop + 29, lc = 0xCCCCCC, x = guiLeft + 70, sw = 40;

        graphics.drawString(font, UIText.of("ponderer.ui.create_entity"), lx, y, lc);
        y += 22;
        graphics.drawString(font, UIText.of("ponderer.ui.create_entity.pos"), lx, y, lc);
        y += 22;
        graphics.drawString(font, UIText.of("ponderer.ui.create_entity.orient"), lx, y + 1, lc);
        y += 22;
        graphics.drawString(font, useYawPitch ? UIText.of("ponderer.ui.create_entity.yaw_pitch") : UIText.of("ponderer.ui.create_entity.lookat"), lx, y, lc);
    }

    @Override
    protected void renderFormForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font, useYawPitch ? UIText.of("ponderer.ui.create_entity.yaw_pitch") : UIText.of("ponderer.ui.create_entity.lookat"),
            orientModeButton.getX() + 50, orientModeButton.getY() + 2, 0xFFFFFF);
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
        return s;
    }
}
