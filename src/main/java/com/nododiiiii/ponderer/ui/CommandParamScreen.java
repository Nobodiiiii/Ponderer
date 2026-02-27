package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Reusable parameter input screen for commands.
 * Uses unified button abstractions for JEI, scene selector, and toggle widgets.
 */
public class CommandParamScreen extends AbstractSimiScreen implements JeiAwareScreen {

    // -- Layout constants --
    private static final int WIDTH = 240;
    private static final int LABEL_W = 72;
    private static final int FIELD_H = 16;
    private static final int ROW_H = 22;
    private static final int MARGIN = 12;

    // Unified button dimensions (matching AbstractStepEditorScreen)
    private static final int ACTION_BTN_W = 14;
    private static final int ACTION_BTN_H = 12;
    private static final int TOGGLE_SIZE = 12;
    private static final int BTN_Y_OFFSET = 4;
    private static final int BTN_GAP = 4;
    private static final int FIELD_BTN_GAP = 6;

    // -- Field definitions --

    public sealed interface FieldDef permits TextFieldDef, ChoiceFieldDef, ToggleFieldDef {
    }

    public record TextFieldDef(String id, String labelKey, String hintKey, boolean required,
            @Nullable IdFieldMode jeiMode, boolean sceneSelector, boolean sceneMultiSelect) implements FieldDef {
    }

    public record ChoiceFieldDef(String id, String labelKey, List<String> optionLabelKeys,
            List<String> values) implements FieldDef {
    }

    public record ToggleFieldDef(String id, String labelKey, boolean defaultValue) implements FieldDef {
    }

    // -- Labeled button tracking for unified rendering --

    private record LabeledButton(PonderButton button, String label, int inactiveColor, int activeColor,
            BooleanSupplier isActive) {
    }

    private record ClickableButton(int x, int y, int w, int h, Supplier<String> labelSupplier, Runnable action) {
    }

    // -- State --

    private final List<FieldDef> fieldDefs;
    private final Consumer<Map<String, String>> onExecute;
    private final Map<String, HintableTextFieldWidget> textInputs = new LinkedHashMap<>();
    private final Map<String, Integer> choiceSelections = new HashMap<>();
    private final Map<String, Boolean> toggleStates = new HashMap<>();
    private final Map<String, BoxWidget> toggleWidgets = new HashMap<>();
    private final List<LabeledButton> labeledButtons = new ArrayList<>();
    private final List<ClickableButton> clickableButtons = new ArrayList<>();
    private final Map<String, String> defaultValues = new HashMap<>();

    // Post-build configuration
    private final Map<String, String> toggleDependencies = new HashMap<>();
    private final Map<String, String> fieldDisablesToggle = new HashMap<>();
    private final Map<String, Map<String, Supplier<String>>> toggleAutoFill = new HashMap<>();

    @Nullable
    private String errorMessage;

    // JEI state
    private boolean jeiActive = false;
    @Nullable
    private HintableTextFieldWidget jeiTargetField = null;

    // Suppress text field responder during programmatic setValue
    private boolean suppressFieldResponder = false;

    private CommandParamScreen(Component title, List<FieldDef> fieldDefs, Consumer<Map<String, String>> onExecute) {
        super(title);
        this.fieldDefs = fieldDefs;
        this.onExecute = onExecute;
    }

    // -- Post-build configuration --

    public void setDefaultValue(String fieldId, String value) {
        defaultValues.put(fieldId, value);
    }

    /** Toggle childToggleId can only be true if parentToggleId is also true. */
    public void addToggleDependency(String childToggleId, String parentToggleId) {
        toggleDependencies.put(childToggleId, parentToggleId);
    }

    /** When the user edits fieldId, automatically set toggleId to false. */
    public void addFieldDisablesToggle(String fieldId, String toggleId) {
        fieldDisablesToggle.put(fieldId, toggleId);
    }

    /**
     * When toggleId is turned ON, auto-fill fieldId with the value from supplier.
     */
    public void addToggleAutoFill(String toggleId, String fieldId, Supplier<String> valueSupplier) {
        toggleAutoFill.computeIfAbsent(toggleId, k -> new HashMap<>()).put(fieldId, valueSupplier);
    }

    // -- Builder --

    public static Builder builder(String titleKey) {
        return new Builder(titleKey);
    }

    public static class Builder {
        private final String titleKey;
        private final List<FieldDef> fields = new ArrayList<>();
        private Consumer<Map<String, String>> onExecute = m -> {
        };

        private Builder(String titleKey) {
            this.titleKey = titleKey;
        }

        public Builder textField(String id, String labelKey, String hintKey, boolean required) {
            fields.add(new TextFieldDef(id, labelKey, hintKey, required, null, false, false));
            return this;
        }

        public Builder itemField(String id, String labelKey, String hintKey, boolean required) {
            fields.add(new TextFieldDef(id, labelKey, hintKey, required, IdFieldMode.ITEM, false, false));
            return this;
        }

        public Builder sceneIdField(String id, String labelKey, String hintKey, boolean required) {
            fields.add(new TextFieldDef(id, labelKey, hintKey, required, null, true, false));
            return this;
        }

        public Builder sceneIdField(String id, String labelKey, String hintKey, boolean required, boolean multiSelect) {
            fields.add(new TextFieldDef(id, labelKey, hintKey, required, null, true, multiSelect));
            return this;
        }

        public Builder choiceField(String id, String labelKey, List<String> optionLabelKeys, List<String> values) {
            fields.add(new ChoiceFieldDef(id, labelKey, optionLabelKeys, values));
            return this;
        }

        public Builder toggleField(String id, String labelKey, boolean defaultValue) {
            fields.add(new ToggleFieldDef(id, labelKey, defaultValue));
            return this;
        }

        public Builder onExecute(Consumer<Map<String, String>> callback) {
            this.onExecute = callback;
            return this;
        }

        public CommandParamScreen build() {
            return new CommandParamScreen(Component.translatable(titleKey), List.copyOf(fields), onExecute);
        }
    }

    // -- Unified button helpers --

    private PonderButton createActionButton(int x, int y, String label,
            int inactiveColor, int activeColor,
            BooleanSupplier isActive, Runnable callback) {
        PonderButton btn = new PonderButton(x, y + BTN_Y_OFFSET, ACTION_BTN_W, ACTION_BTN_H);
        btn.withCallback(callback);
        addRenderableWidget(btn);
        labeledButtons.add(new LabeledButton(btn, label, inactiveColor, activeColor, isActive));
        return btn;
    }

    private BoxWidget createToggleWidget(int fieldX, int y, String id, boolean defaultValue) {
        toggleStates.putIfAbsent(id, defaultValue);
        PonderButton toggle = new PonderButton(fieldX + 3, y + BTN_Y_OFFSET, TOGGLE_SIZE, TOGGLE_SIZE);
        toggle.withCallback(() -> handleToggle(id));
        addRenderableWidget(toggle);
        toggleWidgets.put(id, toggle);
        return toggle;
    }

    private void handleToggle(String id) {
        boolean newState = !toggleStates.getOrDefault(id, false);
        // Check dependency: can't enable if parent is off
        String parent = toggleDependencies.get(id);
        if (parent != null && newState && !toggleStates.getOrDefault(parent, false)) {
            return;
        }
        toggleStates.put(id, newState);
        if (newState) {
            // Auto-fill fields when toggle is turned ON
            Map<String, Supplier<String>> fills = toggleAutoFill.get(id);
            if (fills != null) {
                for (var fe : fills.entrySet()) {
                    HintableTextFieldWidget field = textInputs.get(fe.getKey());
                    if (field != null) {
                        String val = fe.getValue().get();
                        if (val != null && !val.isEmpty()) {
                            suppressFieldResponder = true;
                            field.setValue(val);
                            suppressFieldResponder = false;
                        }
                    }
                }
            }
        } else {
            // If toggled off, cascade to disable all children
            for (var entry : toggleDependencies.entrySet()) {
                if (entry.getValue().equals(id)) {
                    toggleStates.put(entry.getKey(), false);
                }
            }
        }
    }

    private void renderToggleState(GuiGraphics graphics, BoxWidget toggle, boolean state) {
        String label = state ? "V" : "X";
        int color = state ? 0xFF_55FF55 : 0xFF_FF5555;
        var font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font, label, toggle.getX() + 7, toggle.getY() + 2, color);
    }

    // -- Layout --

    private int getWindowHeight() {
        return 36 + fieldDefs.size() * ROW_H + 40;
    }

    @Override
    protected void init() {
        setWindowSize(WIDTH, getWindowHeight());
        super.init();

        textInputs.clear();
        toggleWidgets.clear();
        labeledButtons.clear();
        clickableButtons.clear();
        errorMessage = null;

        int wH = getWindowHeight();
        int fieldY = guiTop + 30;

        for (FieldDef def : fieldDefs) {
            int fieldX = guiLeft + MARGIN + LABEL_W + 4;
            int fieldW = WIDTH - MARGIN * 2 - LABEL_W - 4;

            if (def instanceof TextFieldDef tf) {
                // Count extra buttons to reserve space
                int extraBtns = 0;
                if (tf.jeiMode != null && JeiCompat.isAvailable())
                    extraBtns++;
                if (tf.sceneSelector)
                    extraBtns++;
                int btnSpace = extraBtns > 0
                        ? FIELD_BTN_GAP + extraBtns * ACTION_BTN_W + Math.max(0, extraBtns - 1) * BTN_GAP
                        : 0;
                int actualFieldW = fieldW - btnSpace;

                var font = Minecraft.getInstance().font;
                HintableTextFieldWidget field = new SoftHintTextFieldWidget(font, fieldX, fieldY + 2, actualFieldW,
                        FIELD_H);
                field.setHint(UIText.of(tf.hintKey));
                field.setMaxLength(256);
                addRenderableWidget(field);
                textInputs.put(tf.id, field);

                // Apply default value (with responder suppressed)
                String defaultVal = defaultValues.get(tf.id);
                if (defaultVal != null && !defaultVal.isEmpty()) {
                    suppressFieldResponder = true;
                    field.setValue(defaultVal);
                    suppressFieldResponder = false;
                }

                // Attach responder to disable toggle when user edits manually
                String toggleToDisable = fieldDisablesToggle.get(tf.id);
                if (toggleToDisable != null) {
                    field.setResponder(text -> {
                        if (!suppressFieldResponder) {
                            toggleStates.put(toggleToDisable, false);
                            // Cascade to children
                            for (var dep : toggleDependencies.entrySet()) {
                                if (dep.getValue().equals(toggleToDisable)) {
                                    toggleStates.put(dep.getKey(), false);
                                }
                            }
                        }
                    });
                }

                int btnX = fieldX + actualFieldW + FIELD_BTN_GAP;

                // JEI button
                if (tf.jeiMode != null && JeiCompat.isAvailable()) {
                    final IdFieldMode mode = tf.jeiMode;
                    final HintableTextFieldWidget thisField = field;
                    createActionButton(btnX, fieldY, "J", 0xAAAAFF, 0x55FF55,
                            () -> jeiActive && jeiTargetField == thisField,
                            () -> {
                                if (jeiActive && jeiTargetField == thisField) {
                                    deactivateJei();
                                } else {
                                    jeiActive = true;
                                    jeiTargetField = thisField;
                                    JeiCompat.setActiveScreen(this, mode);
                                }
                            });
                    btnX += ACTION_BTN_W + BTN_GAP;
                }

                // Scene selector button
                if (tf.sceneSelector) {
                    final String fieldId = tf.id;
                    final boolean multiSelect = tf.sceneMultiSelect;
                    createActionButton(btnX, fieldY, "S", 0x80FFFF, 0x80FFFF,
                            () -> false,
                            () -> openSceneSelector(fieldId, multiSelect));
                }
            } else if (def instanceof ChoiceFieldDef cf) {
                choiceSelections.putIfAbsent(cf.id, 0);
                final ChoiceFieldDef choiceDef = cf;
                clickableButtons.add(new ClickableButton(fieldX, fieldY + 1, fieldW, FIELD_H + 2,
                        () -> UIText.of(choiceDef.optionLabelKeys.get(choiceSelections.getOrDefault(choiceDef.id, 0))),
                        () -> cycleChoice(choiceDef)));
            } else if (def instanceof ToggleFieldDef tg) {
                createToggleWidget(fieldX, fieldY, tg.id, tg.defaultValue);
            }
            fieldY += ROW_H;
        }

        // Focus first text field
        for (HintableTextFieldWidget field : textInputs.values()) {
            field.setFocused(true);
            setFocused(field);
            break;
        }

        // Execute button
        int btnY = guiTop + wH - 32;
        clickableButtons.add(new ClickableButton(guiLeft + MARGIN, btnY, 70, 20,
                () -> UIText.of("ponderer.ui.function_page.execute"), this::doExecute));

        // Back button
        clickableButtons.add(new ClickableButton(guiLeft + WIDTH - MARGIN - 70, btnY, 70, 20,
                () -> UIText.of("ponderer.ui.function_page.back"), this::goBack));
    }

    // -- Scene selector --

    private void openSceneSelector(String targetFieldId, boolean multiSelect) {
        List<DslScene> scenes = SceneRuntime.getScenes();
        if (scenes.isEmpty()) {
            errorMessage = UIText.of("ponderer.ui.function_page.no_scenes");
            return;
        }
        saveCurrentValues();
        CommandParamScreen self = this;
        if (multiSelect) {
            Minecraft.getInstance().setScreen(new PonderItemGridScreen(
                    selectedIds -> {
                        String joined = String.join(",", selectedIds);
                        self.defaultValues.put(targetFieldId, joined);
                        Minecraft.getInstance().setScreen(self);
                    },
                    () -> Minecraft.getInstance().setScreen(self),
                    true));
        } else {
            Minecraft.getInstance().setScreen(new PonderItemGridScreen(
                    sceneId -> {
                        self.defaultValues.put(targetFieldId, sceneId);
                        Minecraft.getInstance().setScreen(self);
                    },
                    () -> Minecraft.getInstance().setScreen(self)));
        }
    }

    private void saveCurrentValues() {
        for (var entry : textInputs.entrySet()) {
            String value = entry.getValue().getValue();
            if (value != null && !value.isEmpty()) {
                defaultValues.put(entry.getKey(), value);
            } else {
                defaultValues.remove(entry.getKey());
            }
        }
    }

    // -- Choice cycling --

    private void cycleChoice(ChoiceFieldDef cf) {
        int sel = choiceSelections.getOrDefault(cf.id, 0);
        sel = (sel + 1) % cf.values.size();
        choiceSelections.put(cf.id, sel);
    }

    // -- Execute / Back --

    private void doExecute() {
        Map<String, String> values = new HashMap<>();
        for (FieldDef def : fieldDefs) {
            if (def instanceof TextFieldDef tf) {
                String val = textInputs.get(tf.id).getValue().trim();
                if (tf.required && val.isEmpty()) {
                    errorMessage = UIText.of("ponderer.ui.error.required_field", UIText.of(tf.labelKey));
                    return;
                }
                values.put(tf.id, val);
            } else if (def instanceof ChoiceFieldDef cf) {
                int sel = choiceSelections.getOrDefault(cf.id, 0);
                values.put(cf.id, cf.values.get(sel));
            } else if (def instanceof ToggleFieldDef tg) {
                values.put(tg.id, String.valueOf(toggleStates.getOrDefault(tg.id, tg.defaultValue)));
            }
        }
        errorMessage = null;
        Minecraft.getInstance().setScreen(null);
        onExecute.accept(values);
    }

    private void goBack() {
        if (jeiActive)
            deactivateJei();
        Minecraft.getInstance().setScreen(new FunctionScreen());
    }

    // -- Rendering --

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int wH = getWindowHeight();

        // Background panel
        new BoxElement()
                .withBackground(new Color(0xdd_000000, true))
                .gradientBorder(new Color(0x60_c0c0ff, true), new Color(0x30_c0c0ff, true))
                .at(guiLeft, guiTop, 0)
                .withBounds(WIDTH, wH)
                .render(graphics);

        var font = Minecraft.getInstance().font;

        // Title
        graphics.drawCenteredString(font, this.title, guiLeft + WIDTH / 2, guiTop + 8, 0xFFFFFF);
        graphics.fill(guiLeft + 5, guiTop + 20, guiLeft + WIDTH - 5, guiTop + 21, 0x60_FFFFFF);

        // Field labels
        int fieldY = guiTop + 30;
        for (FieldDef def : fieldDefs) {
            String labelKey;
            if (def instanceof TextFieldDef tf)
                labelKey = tf.labelKey;
            else if (def instanceof ChoiceFieldDef cf)
                labelKey = cf.labelKey;
            else if (def instanceof ToggleFieldDef tg)
                labelKey = tg.labelKey;
            else
                labelKey = "";
            graphics.drawString(font, UIText.of(labelKey), guiLeft + MARGIN, fieldY + 5, 0xCCCCCC);
            fieldY += ROW_H;
        }

        // Error message
        if (errorMessage != null) {
            graphics.drawCenteredString(font, errorMessage, guiLeft + WIDTH / 2, guiTop + wH - 44, 0xFF6666);
        }

        // Simi-style clickable buttons
        for (ClickableButton btn : clickableButtons) {
            boolean hovered = mouseX >= btn.x && mouseX < btn.x + btn.w
                    && mouseY >= btn.y && mouseY < btn.y + btn.h;
            int bgColor = hovered ? 0x80_4466aa : 0x60_333366;
            int borderColor = hovered ? 0xCC_6688cc : 0x60_555588;
            graphics.fill(btn.x, btn.y, btn.x + btn.w, btn.y + btn.h, bgColor);
            graphics.fill(btn.x, btn.y, btn.x + btn.w, btn.y + 1, borderColor);
            graphics.fill(btn.x, btn.y + btn.h - 1, btn.x + btn.w, btn.y + btn.h, borderColor);
            graphics.fill(btn.x, btn.y, btn.x + 1, btn.y + btn.h, borderColor);
            graphics.fill(btn.x + btn.w - 1, btn.y, btn.x + btn.w, btn.y + btn.h, borderColor);
            String label = btn.labelSupplier.get();
            int textWidth = font.width(label);
            int textX = btn.x + (btn.w - textWidth) / 2;
            int textY = btn.y + (btn.h - font.lineHeight) / 2 + 1;
            graphics.drawString(font, label, textX, textY, hovered ? 0xFFFFFF : 0xCCCCCC);
        }
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Foreground overlay (above widgets, z=500)
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);

        var font = Minecraft.getInstance().font;

        // Unified toggle rendering
        for (var entry : toggleWidgets.entrySet()) {
            boolean state = toggleStates.getOrDefault(entry.getKey(), false);
            renderToggleState(graphics, entry.getValue(), state);
        }

        // Unified action button label rendering
        for (LabeledButton lb : labeledButtons) {
            int color = lb.isActive.getAsBoolean() ? lb.activeColor : lb.inactiveColor;
            graphics.drawCenteredString(font, lb.label,
                    lb.button.getX() + 7, lb.button.getY() + 2, color);
        }

        graphics.pose().popPose();
    }

    // -- Input handling --

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (ClickableButton btn : clickableButtons) {
                if (mouseX >= btn.x && mouseX < btn.x + btn.w
                        && mouseY >= btn.y && mouseY < btn.y + btn.h) {
                    btn.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE)
            return super.keyPressed(keyCode, scanCode, modifiers);
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            doExecute();
            return true;
        }
        if (getFocused() != null && getFocused().keyPressed(keyCode, scanCode, modifiers))
            return true;
        if (getFocused() instanceof net.minecraft.client.gui.components.EditBox)
            return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (getFocused() != null && getFocused().charTyped(codePoint, modifiers))
            return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void onClose() {
        goBack();
    }

    @Override
    public void removed() {
        super.removed();
        if (jeiActive)
            deactivateJei();
    }

    // -- JeiAwareScreen implementation --

    @Override
    @Nullable
    public HintableTextFieldWidget getJeiTargetField() {
        return jeiTargetField;
    }

    @Override
    public void deactivateJei() {
        jeiActive = false;
        jeiTargetField = null;
        JeiCompat.clearActiveEditor();
    }

    @Override
    public void showJeiIncompatibleWarning(IdFieldMode mode) {
        errorMessage = switch (mode) {
            case BLOCK -> UIText.of("ponderer.ui.jei.error.not_block");
            case ENTITY -> UIText.of("ponderer.ui.jei.error.not_spawn_egg");
            case ITEM, INGREDIENT -> null;
        };
    }

    @Override
    public int getGuiLeft() {
        return guiLeft;
    }

    @Override
    public int getGuiTop() {
        return guiTop;
    }

    @Override
    public int getGuiWidth() {
        return WIDTH;
    }

    @Override
    public int getGuiHeight() {
        return getWindowHeight();
    }

}
