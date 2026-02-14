package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Editor for "shared_text" step.
 * Fields: key, point XYZ, duration, color, placeNearTarget, attachKeyFrame
 */
public class SharedTextScreen extends AbstractStepEditorScreen {

    private static final String[] COLORS = {
        "", "white", "black", "red", "green", "blue",
        "input", "output", "slow", "medium", "fast"
    };

    private HintableTextFieldWidget keyField;
    private HintableTextFieldWidget pointXField, pointYField, pointZField;
    private HintableTextFieldWidget durationField;
    private int colorIndex = 0;
    private BoxWidget colorButton;
    private boolean placeNearTarget = false;
    private BoxWidget placeToggle;

    public SharedTextScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.shared_text"), scene, sceneIndex, parent);
    }

    public SharedTextScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                            int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.shared_text"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override protected int getFormRowCount() { return 5; }
    @Override protected String getHeaderTitle() { return UIText.of("ponderer.ui.shared_text"); }

    @Override
    protected void buildForm() {
        int x = guiLeft + 70, y = guiTop + 26, sw = 40;
        int lx = guiLeft + 10;

        keyField = createTextField(x, y, 140, 18, UIText.of("ponderer.ui.shared_text.key.hint"));
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui.shared_text.key"), UIText.of("ponderer.ui.shared_text.key.tooltip"));
        y += 22;
        pointXField = createSmallNumberField(x, y, sw, "X");
        pointYField = createSmallNumberField(x + sw + 5, y, sw, "Y");
        pointZField = createSmallNumberField(x + 2 * (sw + 5), y, sw, "Z");
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui.point"), UIText.of("ponderer.ui.point.tooltip"));
        y += 22;
        durationField = createSmallNumberField(x, y, 50, "60");
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui.duration"), UIText.of("ponderer.ui.duration.tooltip.text"));
        y += 22;
        colorButton = createFormButton(x, y, 100);
        colorButton.withCallback(() -> colorIndex = (colorIndex + 1) % COLORS.length);
        addRenderableWidget(colorButton);
        addLabelTooltip(lx, y + 1, UIText.of("ponderer.ui.color"), UIText.of("ponderer.ui.color.tooltip"));
        y += 22;
        placeToggle = createToggle(x, y);
        placeToggle.withCallback(() -> placeNearTarget = !placeNearTarget);
        addRenderableWidget(placeToggle);
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui.place_near"), UIText.of("ponderer.ui.place_near.tooltip"));
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.key != null) keyField.setValue(step.key);
        if (step.point != null && step.point.size() >= 3) {
            pointXField.setValue(String.valueOf(step.point.get(0)));
            pointYField.setValue(String.valueOf(step.point.get(1)));
            pointZField.setValue(String.valueOf(step.point.get(2)));
        }
        if (step.duration != null) durationField.setValue(String.valueOf(step.duration));
        if (step.color != null) {
            for (int i = 0; i < COLORS.length; i++) {
                if (COLORS[i].equalsIgnoreCase(step.color)) { colorIndex = i; break; }
            }
        }
        placeNearTarget = Boolean.TRUE.equals(step.placeNearTarget);
    }

    @Override
    protected void renderForm(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        int lx = guiLeft + 10, y = guiTop + 29, lc = 0xCCCCCC;

        graphics.drawString(font, UIText.of("ponderer.ui.shared_text.key"), lx, y, lc);
        y += 22;
        graphics.drawString(font, UIText.of("ponderer.ui.point"), lx, y, lc);
        y += 22;
        graphics.drawString(font, UIText.of("ponderer.ui.duration"), lx, y, lc);
        graphics.drawString(font, UIText.of("ponderer.ui.ticks"), guiLeft + 130, y, 0x808080);
        y += 22;
        graphics.drawString(font, UIText.of("ponderer.ui.color"), lx, y + 1, lc);
        y += 22;
        graphics.drawString(font, UIText.of("ponderer.ui.place_near"), lx, y + 3, lc);
    }

    @Override
    protected void renderFormForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        // Color button label
        String colorLabel = colorIndex == 0 ? UIText.of("ponderer.ui.none") : colorLabel(COLORS[colorIndex]);
        int colorRgb = colorIndex == 0 ? 0xFFFFFF : getPaletteColor(COLORS[colorIndex]);
        graphics.drawCenteredString(font, colorLabel, colorButton.getX() + 50, colorButton.getY() + 2, colorRgb);
        // Place near toggle
        renderToggleState(graphics, placeToggle, placeNearTarget);
    }

    private String colorLabel(String value) {
        String key = "ponderer.ui.color.option." + value;
        String translated = UIText.of(key);
        return key.equals(translated) ? value : translated;
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;
        String key = keyField.getValue().trim();
        if (key.isEmpty()) { errorMessage = UIText.of("ponderer.ui.shared_text.error.required"); return null; }

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "shared_text";
        s.key = key;
        Double px = parseDouble(pointXField.getValue(), "X");
        Double py = parseDouble(pointYField.getValue(), "Y");
        Double pz = parseDouble(pointZField.getValue(), "Z");
        if (px == null || py == null || pz == null) return null;
        s.point = List.of(px, py, pz);
        s.duration = parseIntOr(durationField.getValue(), 60);
        if (colorIndex > 0) s.color = COLORS[colorIndex];
        if (placeNearTarget) s.placeNearTarget = true;
        return s;
    }
}
