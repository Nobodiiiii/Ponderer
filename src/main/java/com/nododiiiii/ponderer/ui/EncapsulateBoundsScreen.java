package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Editor for "encapsulate_bounds" step.
 * Fields: bounds X, Y, Z (3 integers defining the bounding box size).
 */
public class EncapsulateBoundsScreen extends AbstractStepEditorScreen {

    private HintableTextFieldWidget boundsXField, boundsYField, boundsZField;

    public EncapsulateBoundsScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.encapsulate_bounds"), scene, sceneIndex, parent);
    }

    public EncapsulateBoundsScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                   int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.encapsulate_bounds"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override protected int getFormRowCount() { return 1; }
    @Override protected String getHeaderTitle() { return UIText.of("ponderer.ui.encapsulate_bounds"); }

    @Override
    protected void buildForm() {
        int x = guiLeft + 70, y = guiTop + 26, sw = 40;
        boundsXField = createSmallNumberField(x, y, sw, "X");
        boundsYField = createSmallNumberField(x + sw + 5, y, sw, "Y");
        boundsZField = createSmallNumberField(x + 2 * (sw + 5), y, sw, "Z");
        addLabelTooltip(guiLeft + 10, y + 3, UIText.of("ponderer.ui.encapsulate_bounds.bounds"), UIText.of("ponderer.ui.encapsulate_bounds.bounds.tooltip"));
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.bounds != null && step.bounds.size() >= 3) {
            boundsXField.setValue(String.valueOf(step.bounds.get(0)));
            boundsYField.setValue(String.valueOf(step.bounds.get(1)));
            boundsZField.setValue(String.valueOf(step.bounds.get(2)));
        }
    }

    @Override
    protected void renderForm(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        int lx = guiLeft + 10, y = guiTop + 29, lc = 0xCCCCCC;
        graphics.drawString(font, UIText.of("ponderer.ui.encapsulate_bounds.bounds"), lx, y, lc);
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;
        Integer bx = parseInt(boundsXField.getValue(), "X");
        Integer by = parseInt(boundsYField.getValue(), "Y");
        Integer bz = parseInt(boundsZField.getValue(), "Z");
        if (bx == null || by == null || bz == null) return null;

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "encapsulate_bounds";
        s.bounds = List.of(bx, by, bz);
        return s;
    }
}
