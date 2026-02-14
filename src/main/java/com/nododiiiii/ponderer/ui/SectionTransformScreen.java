package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;

public class SectionTransformScreen extends AbstractStepEditorScreen {

    private final String stepType;
    private final boolean rotationMode;

    private HintableTextFieldWidget linkIdField;
    private HintableTextFieldWidget xField, yField, zField;
    private HintableTextFieldWidget durationField;

    public SectionTransformScreen(String stepType, boolean rotationMode,
                                  DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui." + stepType + ".add"), scene, sceneIndex, parent);
        this.stepType = stepType;
        this.rotationMode = rotationMode;
    }

    public SectionTransformScreen(String stepType, boolean rotationMode,
                                  DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                  int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui." + stepType + ".edit"), scene, sceneIndex, parent, editIndex, step);
        this.stepType = stepType;
        this.rotationMode = rotationMode;
    }

    @Override
    protected int getFormRowCount() { return 3; }

    @Override
    protected String getHeaderTitle() { return UIText.of("ponderer.ui." + stepType); }

    @Override
    protected void buildForm() {
        int x = guiLeft + 70, y = guiTop + 26, sw = 40;
        int lx = guiLeft + 10;

        linkIdField = createTextField(x, y, 140, 18, "default");
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui." + stepType + ".link"), UIText.of("ponderer.ui." + stepType + ".link.tooltip"));
        y += 22;

        xField = createSmallNumberField(x, y, sw, "X");
        yField = createSmallNumberField(x + sw + 5, y, sw, "Y");
        zField = createSmallNumberField(x + 2 * (sw + 5), y, sw, "Z");
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui." + stepType + ".xyz"), UIText.of("ponderer.ui." + stepType + ".xyz.tooltip"));
        y += 22;

        durationField = createSmallNumberField(x, y, 60, "20");
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui.duration"), UIText.of("ponderer.ui.duration.tooltip.idle"));
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.linkId != null) linkIdField.setValue(step.linkId);
        if (rotationMode) {
            if (step.rotX != null) xField.setValue(String.valueOf(step.rotX));
            if (step.rotY != null) yField.setValue(String.valueOf(step.rotY));
            if (step.rotZ != null) zField.setValue(String.valueOf(step.rotZ));
        } else if (step.offset != null && step.offset.size() >= 3) {
            xField.setValue(String.valueOf(step.offset.get(0)));
            yField.setValue(String.valueOf(step.offset.get(1)));
            zField.setValue(String.valueOf(step.offset.get(2)));
        }
        if (step.duration != null) durationField.setValue(String.valueOf(step.duration));
    }

    @Override
    protected void renderForm(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        int lx = guiLeft + 10, y = guiTop + 29, lc = 0xCCCCCC;
        graphics.drawString(font, UIText.of("ponderer.ui." + stepType + ".link"), lx, y, lc);
        y += 22;
        graphics.drawString(font, UIText.of("ponderer.ui." + stepType + ".xyz"), lx, y, lc);
        y += 22;
        graphics.drawString(font, UIText.of("ponderer.ui.duration"), lx, y, lc);
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;

        Double x = parseDouble(xField.getValue(), "X");
        Double y = parseDouble(yField.getValue(), "Y");
        Double z = parseDouble(zField.getValue(), "Z");
        if (x == null || y == null || z == null) return null;

        Integer duration = parseInt(durationField.getValue(), UIText.of("ponderer.ui.duration"));
        if (duration == null) return null;

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = stepType;
        String link = linkIdField.getValue().trim();
        if (!link.isEmpty()) s.linkId = link;
        s.duration = Math.max(0, duration);

        if (rotationMode) {
            s.rotX = x.floatValue();
            s.rotY = y.floatValue();
            s.rotZ = z.floatValue();
        } else {
            s.offset = List.of(x, y, z);
        }

        return s;
    }
}
