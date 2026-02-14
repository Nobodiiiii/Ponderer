package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/** Editor for "idle" step - duration in ticks. */
public class IdleScreen extends AbstractStepEditorScreen {

    private HintableTextFieldWidget durationField;

    public IdleScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.idle"), scene, sceneIndex, parent);
    }

    public IdleScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                      int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.idle"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override protected int getFormRowCount() { return 1; }
    @Override protected String getHeaderTitle() { return UIText.of("ponderer.ui.idle"); }

    @Override
    protected void buildForm() {
        int x = guiLeft + 70, y = guiTop + FORM_TOP;
        durationField = createSmallNumberField(x, y, 60, "20");
        addLabelTooltip(guiLeft + 10, y + 3, UIText.of("ponderer.ui.duration"), UIText.of("ponderer.ui.duration.tooltip.idle"));
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.duration != null) durationField.setValue(String.valueOf(step.duration));
    }

    @Override
    protected void renderForm(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, UIText.of("ponderer.ui.duration"), guiLeft + 10, guiTop + FORM_TOP + 3, 0xCCCCCC);
        graphics.drawString(font, UIText.of("ponderer.ui.ticks"), guiLeft + 140, guiTop + FORM_TOP + 3, 0x808080);
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;
        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "idle";
        s.duration = parseIntOr(durationField.getValue(), 20);
        if (s.duration < 0) { errorMessage = UIText.of("ponderer.ui.idle.error.duration"); return null; }
        return s;
    }
}
