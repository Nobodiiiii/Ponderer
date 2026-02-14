package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;

public class DestroyBlockScreen extends AbstractStepEditorScreen {

    private net.createmod.catnip.config.ui.HintableTextFieldWidget posXField, posYField, posZField;
    private boolean destroyParticles = true;
    private BoxWidget particlesToggle;

    public DestroyBlockScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.destroy_block.add"), scene, sceneIndex, parent);
    }

    public DestroyBlockScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                              int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.destroy_block.edit"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override
    protected int getFormRowCount() {
        return 2;
    }

    @Override
    protected String getHeaderTitle() {
        return UIText.of("ponderer.ui.destroy_block");
    }

    @Override
    protected void buildForm() {
        int x = guiLeft + 70, y = guiTop + 26, sw = 40;
        int lx = guiLeft + 10;

        posXField = createSmallNumberField(x, y, sw, "X");
        posYField = createSmallNumberField(x + sw + 5, y, sw, "Y");
        posZField = createSmallNumberField(x + 2 * (sw + 5), y, sw, "Z");
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui.destroy_block.pos"), UIText.of("ponderer.ui.destroy_block.pos.tooltip"));

        y += 22;
        particlesToggle = createToggle(x, y);
        particlesToggle.withCallback(() -> destroyParticles = !destroyParticles);
        addRenderableWidget(particlesToggle);
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui.destroy_block.particles"), UIText.of("ponderer.ui.destroy_block.particles.tooltip"));
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.blockPos != null && step.blockPos.size() >= 3) {
            posXField.setValue(String.valueOf(step.blockPos.get(0)));
            posYField.setValue(String.valueOf(step.blockPos.get(1)));
            posZField.setValue(String.valueOf(step.blockPos.get(2)));
        }
        if (step.destroyParticles != null) {
            destroyParticles = step.destroyParticles;
        }
    }

    @Override
    protected void renderForm(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        int lx = guiLeft + 10, y = guiTop + 29, lc = 0xCCCCCC;
        graphics.drawString(font, UIText.of("ponderer.ui.destroy_block.pos"), lx, y, lc);
        y += 22;
        graphics.drawString(font, UIText.of("ponderer.ui.destroy_block.particles"), lx, y + 3, lc);
    }

    @Override
    protected void renderFormForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        renderToggleState(graphics, particlesToggle, destroyParticles);
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;

        Integer px = parseInt(posXField.getValue(), "X");
        Integer py = parseInt(posYField.getValue(), "Y");
        Integer pz = parseInt(posZField.getValue(), "Z");
        if (px == null || py == null || pz == null) return null;

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "destroy_block";
        s.blockPos = List.of(px, py, pz);
        if (!destroyParticles) s.destroyParticles = false;
        return s;
    }
}
