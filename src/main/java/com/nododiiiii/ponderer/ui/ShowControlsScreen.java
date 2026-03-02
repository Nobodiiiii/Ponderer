package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Editor for "show_controls" step.
 * Fields: point XYZ, direction, duration, action, item, whileSneaking, whileCTRL
 */
public class ShowControlsScreen extends AbstractStepEditorScreen {

    private static final String[] DIRECTIONS = {"down", "up", "left", "right"};
    private static final String[] ACTIONS = {"", "left", "right", "scroll"};

    private HintableTextFieldWidget pointXField, pointYField, pointZField;
    private HintableTextFieldWidget durationField;
    private HintableTextFieldWidget itemField;
    private int dirIndex = 0;
    private int actionIndex = 0;
    private BoxWidget dirButton, actionButton;
    private boolean whileSneaking = false, whileCTRL = false;
    private BoxWidget sneakToggle, ctrlToggle;
    private PonderButton pickBtnPoint;
    @Nullable
    private PonderButton jeiBtn;

    public ShowControlsScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.show_controls"), scene, sceneIndex, parent);
    }

    public ShowControlsScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                              int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.show_controls"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override protected int getFormRowCount() { return 7; }
    @Override protected String getHeaderTitle() { return UIText.of("ponderer.ui.show_controls"); }

    @Override
    protected void buildForm() {
        int x = guiLeft + 70, y = guiTop + 26, sw = 38;
        int lx = guiLeft + 10;

        pointXField = createSmallNumberField(x, y, sw, "X");
        pointYField = createSmallNumberField(x + sw + 5, y, sw, "Y");
        pointZField = createSmallNumberField(x + 2 * (sw + 5), y, sw, "Z");
        pickBtnPoint = createPickButton(x + 3 * (sw + 5), y, PickState.TargetField.POINT, true);
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui.point"), UIText.of("ponderer.ui.show_controls.point.tooltip"));
        y += 22;
        dirButton = createFormButton(x, y, 100);
        dirButton.withCallback(() -> dirIndex = (dirIndex + 1) % DIRECTIONS.length);
        addRenderableWidget(dirButton);
        addLabelTooltip(lx, y + 1, UIText.of("ponderer.ui.show_controls.direction"), UIText.of("ponderer.ui.show_controls.direction.tooltip"));
        y += 22;
        durationField = createSmallNumberField(x, y, 50, "60");
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui.duration"), UIText.of("ponderer.ui.duration.tooltip.controls"));
        y += 22;
        actionButton = createFormButton(x, y, 100);
        actionButton.withCallback(() -> actionIndex = (actionIndex + 1) % ACTIONS.length);
        addRenderableWidget(actionButton);
        addLabelTooltip(lx, y + 1, UIText.of("ponderer.ui.show_controls.action"), UIText.of("ponderer.ui.show_controls.action.tooltip"));
        y += 22;
        itemField = createTextField(x, y, 124, 18, UIText.of("ponderer.ui.show_controls.item.hint"));
        jeiBtn = createJeiButton(x + 129, y, itemField, IdFieldMode.INGREDIENT);
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui.show_controls.item"), UIText.of("ponderer.ui.show_controls.item.tooltip"));
        y += 22;
        sneakToggle = createToggle(x, y);
        sneakToggle.withCallback(() -> whileSneaking = !whileSneaking);
        addRenderableWidget(sneakToggle);
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui.show_controls.sneaking"), UIText.of("ponderer.ui.show_controls.sneaking.tooltip"));
        y += 22;
        ctrlToggle = createToggle(x, y);
        ctrlToggle.withCallback(() -> whileCTRL = !whileCTRL);
        addRenderableWidget(ctrlToggle);
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui.show_controls.ctrl"), UIText.of("ponderer.ui.show_controls.ctrl.tooltip"));
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.point != null && step.point.size() >= 3) {
            pointXField.setValue(String.valueOf(step.point.get(0)));
            pointYField.setValue(String.valueOf(step.point.get(1)));
            pointZField.setValue(String.valueOf(step.point.get(2)));
        }
        if (step.direction != null) {
            for (int i = 0; i < DIRECTIONS.length; i++) {
                if (DIRECTIONS[i].equalsIgnoreCase(step.direction)) { dirIndex = i; break; }
            }
        }
        if (step.duration != null) durationField.setValue(String.valueOf(step.duration));
        if (step.action != null) {
            for (int i = 0; i < ACTIONS.length; i++) {
                if (ACTIONS[i].equalsIgnoreCase(step.action)) { actionIndex = i; break; }
            }
        }
        if (step.item != null) itemField.setValue(step.item);
        whileSneaking = Boolean.TRUE.equals(step.whileSneaking);
        whileCTRL = Boolean.TRUE.equals(step.whileCTRL);
    }

    @Override
    protected void renderForm(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        int lx = guiLeft + 10, y = guiTop + 29, lc = 0xCCCCCC;

        graphics.drawString(font, UIText.of("ponderer.ui.point"), lx, y, lc);
        y += 22;
        graphics.drawString(font, UIText.of("ponderer.ui.show_controls.direction"), lx, y + 1, lc);
        y += 22;
        graphics.drawString(font, UIText.of("ponderer.ui.duration"), lx, y, lc);
        graphics.drawString(font, UIText.of("ponderer.ui.ticks"), guiLeft + 130, y, 0x808080);
        y += 22;
        graphics.drawString(font, UIText.of("ponderer.ui.show_controls.action"), lx, y + 1, lc);
        y += 22;
        graphics.drawString(font, UIText.of("ponderer.ui.show_controls.item"), lx, y, lc);
        y += 22;
        graphics.drawString(font, UIText.of("ponderer.ui.show_controls.sneaking"), lx, y + 3, lc);
        y += 22;
        graphics.drawString(font, UIText.of("ponderer.ui.show_controls.ctrl"), lx, y + 3, lc);
    }

    @Override
    protected void renderFormForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        // Direction button label
        graphics.drawCenteredString(font, optionLabel("ponderer.ui.show_controls.direction", DIRECTIONS[dirIndex]), dirButton.getX() + 50, dirButton.getY() + 2, 0xFFFFFF);
        // Action button label
        String al = actionIndex == 0 ? UIText.of("ponderer.ui.none") : optionLabel("ponderer.ui.show_controls.action", ACTIONS[actionIndex]);
        graphics.drawCenteredString(font, al, actionButton.getX() + 50, actionButton.getY() + 2, 0xFFFFFF);
        // Sneaking toggle
        renderToggleState(graphics, sneakToggle, whileSneaking);
        // CTRL toggle
        renderToggleState(graphics, ctrlToggle, whileCTRL);
        // Pick button label
        renderPickButtonLabel(graphics, pickBtnPoint);
        renderJeiButtonLabel(graphics, jeiBtn);
    }

    private String optionLabel(String prefix, String value) {
        String key = prefix + "." + value;
        String translated = UIText.of(key);
        return key.equals(translated) ? value : translated;
    }

    @Override
    protected String getStepType() { return "show_controls"; }

    @Override
    protected Map<String, String> snapshotForm() {
        Map<String, String> m = new HashMap<>();
        m.put("pointX", pointXField.getValue());
        m.put("pointY", pointYField.getValue());
        m.put("pointZ", pointZField.getValue());
        m.put("duration", durationField.getValue());
        m.put("item", itemField.getValue());
        m.put("dirIndex", String.valueOf(dirIndex));
        m.put("actionIndex", String.valueOf(actionIndex));
        m.put("whileSneaking", String.valueOf(whileSneaking));
        m.put("whileCTRL", String.valueOf(whileCTRL));
        return m;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        if (snapshot.containsKey("pointX")) pointXField.setValue(snapshot.get("pointX"));
        if (snapshot.containsKey("pointY")) pointYField.setValue(snapshot.get("pointY"));
        if (snapshot.containsKey("pointZ")) pointZField.setValue(snapshot.get("pointZ"));
        if (snapshot.containsKey("duration")) durationField.setValue(snapshot.get("duration"));
        if (snapshot.containsKey("item")) itemField.setValue(snapshot.get("item"));
        if (snapshot.containsKey("dirIndex")) {
            try { dirIndex = Integer.parseInt(snapshot.get("dirIndex")); } catch (NumberFormatException ignored) {}
        }
        if (snapshot.containsKey("actionIndex")) {
            try { actionIndex = Integer.parseInt(snapshot.get("actionIndex")); } catch (NumberFormatException ignored) {}
        }
        if (snapshot.containsKey("whileSneaking")) whileSneaking = Boolean.parseBoolean(snapshot.get("whileSneaking"));
        if (snapshot.containsKey("whileCTRL")) whileCTRL = Boolean.parseBoolean(snapshot.get("whileCTRL"));
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;
        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "show_controls";
        Double px = parseDouble(pointXField.getValue(), "X");
        Double py = parseDouble(pointYField.getValue(), "Y");
        Double pz = parseDouble(pointZField.getValue(), "Z");
        if (px == null || py == null || pz == null) return null;
        s.point = List.of(px, py, pz);
        s.direction = DIRECTIONS[dirIndex];
        s.duration = parseIntOr(durationField.getValue(), 60);
        if (actionIndex > 0) s.action = ACTIONS[actionIndex];
        String item = itemField.getValue().trim();
        if (!item.isEmpty()) s.item = item;
        if (whileSneaking) s.whileSneaking = true;
        if (whileCTRL) s.whileCTRL = true;
        return s;
    }
}
