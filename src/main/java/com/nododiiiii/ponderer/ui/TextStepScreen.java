package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.LocalizedText;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Editor for "text" step.
 * Fields: text, point XYZ, duration, color, placeNearTarget, attachKeyFrame
 */
public class TextStepScreen extends AbstractStepEditorScreen {

    private static final String[] COLORS = {
        "", "white", "black", "red", "green", "blue",
        "input", "output", "slow", "medium", "fast"
    };

    private HintableTextFieldWidget textField;
    private HintableTextFieldWidget pointXField, pointYField, pointZField;
    private HintableTextFieldWidget durationField;
    private int colorIndex = 0;
    private BoxWidget colorButton;
    private boolean placeNearTarget = false;
    private BoxWidget placeToggle;

    /** The language currently being edited; defaults to MC's current language. */
    private String editingLang;
    /** A working copy of the LocalizedText being built up across language switches. */
    private LocalizedText workingText;
    private BoxWidget langButton;

    public TextStepScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.text"), scene, sceneIndex, parent);
        this.editingLang = getCurrentLang();
        this.workingText = LocalizedText.of("");
    }

    public TextStepScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                          int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.text"), scene, sceneIndex, parent, editIndex, step);
        this.editingLang = getCurrentLang();
        // Deep-copy the existing text so edits don't mutate the original until confirm
        this.workingText = step.text != null ? step.text : LocalizedText.of("");
    }

    @Override protected int getFormRowCount() { return 5; }
    @Override protected String getHeaderTitle() { return UIText.of("ponderer.ui.text"); }

    @Override
    protected void buildForm() {
        int x = guiLeft + 70, y = guiTop + 26, sw = 40;
        int lx = guiLeft + 10;

        // Text field (narrower to make room for lang button)
        textField = createTextField(x, y, 108, 18, UIText.of("ponderer.ui.text.hint"));
        // Lang toggle button right of text field
        langButton = createFormButton(x + 112, y, 28);
        langButton.withCallback(this::toggleLang);
        addRenderableWidget(langButton);
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui.text"), UIText.of("ponderer.ui.text.tooltip"));
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
        colorButton.withCallback(() -> { colorIndex = (colorIndex + 1) % COLORS.length; });
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
        if (step.text != null) {
            workingText = step.text;
            String val = workingText.getExact(editingLang);
            textField.setValue(val != null ? val : workingText.resolve());
        }
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

        graphics.drawString(font, UIText.of("ponderer.ui.text"), lx, y, lc);
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
        // Lang button label
        String langLabel = editingLang.length() > 5 ? editingLang.substring(0, 5) : editingLang;
        graphics.drawCenteredString(font, langLabel, langButton.getX() + 14, langButton.getY() + 2, 0xAAFFAA);
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
        String text = textField.getValue();
        if (text.isEmpty()) { errorMessage = UIText.of("ponderer.ui.text.error.required"); return null; }

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "text";
        // Save current field text into the working copy for the editing language
        workingText.setForLang(editingLang, text);
        s.text = workingText;
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

    /** Toggle between editing the current MC language and en_us. */
    private void toggleLang() {
        // Save current text into workingText for the current editingLang
        String currentText = textField.getValue();
        if (!currentText.isEmpty()) {
            workingText.setForLang(editingLang, currentText);
        }

        // Switch language
        String mcLang = getCurrentLang();
        if (editingLang.equals("en_us") && !"en_us".equals(mcLang)) {
            editingLang = mcLang;
        } else {
            editingLang = "en_us";
        }

        // Load text for the new editingLang
        String val = workingText.getExact(editingLang);
        textField.setValue(val != null ? val : "");
    }

    private static String getCurrentLang() {
        try {
            return Minecraft.getInstance().getLanguageManager().getSelected();
        } catch (Exception e) {
            return "en_us";
        }
    }
}
