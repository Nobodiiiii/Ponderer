package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

public class RotateCameraScreen extends AbstractStepEditorScreen {

    private HintableTextFieldWidget degreesField;

    public RotateCameraScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.rotate_camera.add"), scene, sceneIndex, parent);
    }

    public RotateCameraScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                              int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.rotate_camera.edit"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override
    protected int getFormRowCount() { return 1; }

    @Override
    protected String getHeaderTitle() { return UIText.of("ponderer.ui.rotate_camera"); }

    @Override
    protected void buildForm() {
        int x = guiLeft + 70, y = guiTop + FORM_TOP;
        degreesField = createSmallNumberField(x, y, 60, "90");
        addLabelTooltip(guiLeft + 10, y + 3, UIText.of("ponderer.ui.rotate_camera.degrees"), UIText.of("ponderer.ui.rotate_camera.degrees.tooltip"));
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.degrees != null) {
            degreesField.setValue(String.valueOf(step.degrees));
        }
    }

    @Override
    protected void renderForm(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, UIText.of("ponderer.ui.rotate_camera.degrees"), guiLeft + 10, guiTop + FORM_TOP + 3, 0xCCCCCC);
        graphics.drawString(font, UIText.of("ponderer.ui.rotate_camera.degrees.unit"), guiLeft + 136, guiTop + FORM_TOP + 3, 0x808080);
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;
        String raw = degreesField.getValue() == null ? "" : degreesField.getValue().trim();
        raw = raw.replaceAll("[^0-9+\\-\\.]", "").trim();
        if (raw.isEmpty()) {
            raw = "90";
        }

        Float degrees = parseFloat(raw, "Degrees");
        if (degrees == null) {
            return null;
        }

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "rotate_camera_y";
        s.degrees = degrees;
        return s;
    }
}
