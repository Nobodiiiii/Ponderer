package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
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

    public sealed interface FieldDef permits TextFieldDef, ChoiceFieldDef, ToggleFieldDef {}

    public record TextFieldDef(String id, String labelKey, String hintKey, boolean required,
                                @Nullable IdFieldMode jeiMode, boolean sceneSelector) implements FieldDef {}

    public record ChoiceFieldDef(String id, String labelKey, List<String> optionLabelKeys,
                                  List<String> values) implements FieldDef {}

    public record ToggleFieldDef(String id, String labelKey, boolean defaultValue) implements FieldDef {}

    // -- Labeled button tracking for unified rendering --

    private record LabeledButton(PonderButton button, String label, int inactiveColor, int activeColor,
                                  BooleanSupplier isActive) {}

    private record ClickableButton(int x, int y, int w, int h, Supplier<String> labelSupplier, Runnable action) {}

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

    /** When toggleId is turned ON, auto-fill fieldId with the value from supplier. */
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
        private Consumer<Map<String, String>> onExecute = m -> {};

        private Builder(String titleKey) {
            this.titleKey = titleKey;
        }

        public Builder textField(String id, String labelKey, String hintKey, boolean required) {
            fields.add(new TextFieldDef(id, labelKey, hintKey, required, null, false));
            return this;
        }

        public Builder itemField(String id, String labelKey, String hintKey, boolean required) {
            fields.add(new TextFieldDef(id, labelKey, hintKey, required, IdFieldMode.ITEM, false));
            return this;
        }

        public Builder sceneIdField(String id, String labelKey, String hintKey, boolean required) {
            fields.add(new TextFieldDef(id, labelKey, hintKey, required, null, true));
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
                if (tf.jeiMode != null && JeiCompat.isAvailable()) extraBtns++;
                if (tf.sceneSelector) extraBtns++;
                int btnSpace = extraBtns > 0 ? FIELD_BTN_GAP + extraBtns * ACTION_BTN_W + Math.max(0, extraBtns - 1) * BTN_GAP : 0;
                int actualFieldW = fieldW - btnSpace;

                var font = Minecraft.getInstance().font;
                HintableTextFieldWidget field = new SoftHintTextFieldWidget(font, fieldX, fieldY + 2, actualFieldW, FIELD_H);
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
                    createActionButton(btnX, fieldY, "S", 0x80FFFF, 0x80FFFF,
                        () -> false,
                        () -> openSceneSelector(fieldId));
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

    private void openSceneSelector(String targetFieldId) {
        List<DslScene> scenes = SceneRuntime.getScenes();
        if (scenes.isEmpty()) {
            errorMessage = UIText.of("ponderer.ui.function_page.no_scenes");
            return;
        }
        saveCurrentValues();
        CommandParamScreen self = this;
        Minecraft.getInstance().setScreen(new SceneItemPickerScreen(selected -> {
            self.defaultValues.put(targetFieldId, selected);
            Minecraft.getInstance().setScreen(self);
        }, () -> Minecraft.getInstance().setScreen(self)));
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
        if (jeiActive) deactivateJei();
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
            if (def instanceof TextFieldDef tf) labelKey = tf.labelKey;
            else if (def instanceof ChoiceFieldDef cf) labelKey = cf.labelKey;
            else if (def instanceof ToggleFieldDef tg) labelKey = tg.labelKey;
            else labelKey = "";
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
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            doExecute();
            return true;
        }
        if (getFocused() != null && getFocused().keyPressed(keyCode, scanCode, modifiers))
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
        if (jeiActive) deactivateJei();
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
            case ITEM -> null;
        };
    }

    @Override
    public int getGuiLeft() { return guiLeft; }

    @Override
    public int getGuiTop() { return guiTop; }

    @Override
    public int getGuiWidth() { return WIDTH; }

    @Override
    public int getGuiHeight() { return getWindowHeight(); }

    // -- Scene Item Picker (inner screen) --

    static class SceneItemPickerScreen extends AbstractSimiScreen {
        private static final int SEL_W = 256;
        private static final int COLS = 11;
        private static final int ROWS_PER_PAGE = 6;
        private static final int ITEMS_PER_PAGE = COLS * ROWS_PER_PAGE;
        private static final int CELL_SIZE = 20;
        private static final int GRID_LEFT = 14;
        private static final int GRID_TOP = 42;

        private record PickerEntry(ItemStack stack, @Nullable String nbtFilter, List<String> sceneIds) {}

        private final List<PickerEntry> entries;
        private final Consumer<String> onSelect;
        private final Runnable onCancel;
        private int page = 0;
        private int totalPages;

        SceneItemPickerScreen(Consumer<String> onSelect, Runnable onCancel) {
            super(Component.translatable("ponderer.ui.function_page.select_scene"));
            this.onSelect = onSelect;
            this.onCancel = onCancel;

            // Collect items and map each to ALL matching scene IDs
            LinkedHashMap<String, List<String>> itemFilters = new LinkedHashMap<>();
            Map<String, List<String>> allSceneIds = new LinkedHashMap<>();

            for (DslScene scene : SceneRuntime.getScenes()) {
                if (scene.items == null || scene.id == null) continue;
                for (String itemId : scene.items) {
                    String nf = scene.nbtFilter;
                    String key = itemId + "|" + (nf != null ? nf : "");
                    allSceneIds.computeIfAbsent(key, k -> new ArrayList<>());
                    List<String> ids = allSceneIds.get(key);
                    if (!ids.contains(scene.id)) ids.add(scene.id);
                    itemFilters.computeIfAbsent(itemId, k -> new ArrayList<>());
                    List<String> filters = itemFilters.get(itemId);
                    if (nf != null && !nf.isBlank()) {
                        if (!filters.contains(nf)) filters.add(nf);
                    } else {
                        if (!filters.contains(null)) filters.add(0, null);
                    }
                }
            }

            List<PickerEntry> stacks = new ArrayList<>();
            for (var entry : itemFilters.entrySet()) {
                ResourceLocation rl = ResourceLocation.tryParse(entry.getKey());
                if (rl == null) continue;
                Item item = BuiltInRegistries.ITEM.get(rl);
                if (item == null || item == Items.AIR) continue;
                for (String nf : entry.getValue()) {
                    ItemStack stack = new ItemStack(item);
                    if (nf != null) {
                        // Try to apply NBT filter to stack for visual distinction (1.21.1 component model)
                        try {
                            CompoundTag filterTag = TagParser.parseTag(nf);
                            CompoundTag fullTag = new CompoundTag();
                            fullTag.putString("id", rl.toString());
                            fullTag.putInt("count", 1);
                            for (String tagKey : filterTag.getAllKeys()) {
                                fullTag.put(tagKey, filterTag.get(tagKey));
                            }
                            var registryAccess = Minecraft.getInstance().level != null
                                ? Minecraft.getInstance().level.registryAccess() : null;
                            if (registryAccess != null) {
                                ItemStack parsed = ItemStack.parseOptional(registryAccess, fullTag);
                                if (!parsed.isEmpty()) stack = parsed;
                            }
                        } catch (Exception ignored) {}
                    }
                    String key = entry.getKey() + "|" + (nf != null ? nf : "");
                    List<String> sceneIds = allSceneIds.getOrDefault(key, List.of());
                    if (!sceneIds.isEmpty()) {
                        stacks.add(new PickerEntry(stack, nf, sceneIds));
                    }
                }
            }
            this.entries = stacks;
            this.totalPages = Math.max(1, (entries.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        }

        private int getPickerWindowHeight() {
            return GRID_TOP + ROWS_PER_PAGE * CELL_SIZE + 40;
        }

        @Override
        protected void init() {
            setWindowSize(SEL_W, getPickerWindowHeight());
            super.init();
        }

        @Override
        protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            int wH = getPickerWindowHeight();

            new BoxElement()
                .withBackground(new Color(0xdd_000000, true))
                .gradientBorder(new Color(0x60_c0c0ff, true), new Color(0x30_c0c0ff, true))
                .at(guiLeft, guiTop, 0)
                .withBounds(SEL_W, wH)
                .render(graphics);

            var font = Minecraft.getInstance().font;

            graphics.drawCenteredString(font, this.title, guiLeft + SEL_W / 2, guiTop + 8, 0xFFFFFF);
            graphics.fill(guiLeft + 5, guiTop + 20, guiLeft + SEL_W - 5, guiTop + 21, 0x60_FFFFFF);

            String subtitle = UIText.of("ponderer.ui.item_list.count", entries.size());
            graphics.drawString(font, subtitle, guiLeft + 10, guiTop + 25, 0x999999);

            if (entries.isEmpty()) {
                graphics.drawCenteredString(font,
                    Component.translatable("ponderer.ui.function_page.no_scenes"),
                    guiLeft + SEL_W / 2, guiTop + GRID_TOP + 30, 0x999999);
                return;
            }

            int startIdx = page * ITEMS_PER_PAGE;
            int endIdx = Math.min(startIdx + ITEMS_PER_PAGE, entries.size());
            for (int i = startIdx; i < endIdx; i++) {
                int localIdx = i - startIdx;
                int col = localIdx % COLS;
                int row = localIdx / COLS;
                int ix = guiLeft + GRID_LEFT + col * CELL_SIZE;
                int iy = guiTop + GRID_TOP + row * CELL_SIZE;

                PickerEntry entry = entries.get(i);
                if (mouseX >= ix && mouseX < ix + CELL_SIZE && mouseY >= iy && mouseY < iy + CELL_SIZE) {
                    graphics.fill(ix, iy, ix + CELL_SIZE, iy + CELL_SIZE, 0x40_FFFFFF);
                }
                graphics.renderItem(entry.stack, ix + 2, iy + 2);
                if (entry.nbtFilter != null) {
                    graphics.fill(ix + CELL_SIZE - 5, iy + 1, ix + CELL_SIZE - 1, iy + 5, 0xFF_FFAA00);
                }
                // Multi-scene indicator dot (blue)
                if (entry.sceneIds.size() > 1) {
                    graphics.fill(ix + 1, iy + 1, ix + 5, iy + 5, 0xFF_55AAFF);
                }
            }

            if (totalPages > 1) {
                String pageText = UIText.of("ponderer.ui.item_list.page", page + 1, totalPages);
                graphics.drawCenteredString(font, pageText, guiLeft + SEL_W / 2, guiTop + wH - 22, 0xCCCCCC);

                // Simi-style pagination buttons
                int prevX = guiLeft + 10, prevY = guiTop + wH - 26, btnW = 20, btnH = 16;
                int nextX = guiLeft + SEL_W - 30, nextY = prevY;
                for (int bi = 0; bi < 2; bi++) {
                    int bx = bi == 0 ? prevX : nextX;
                    int by = bi == 0 ? prevY : nextY;
                    String lbl = bi == 0 ? "<" : ">";
                    boolean enabled = bi == 0 ? page > 0 : page < totalPages - 1;
                    boolean hov = enabled && mouseX >= bx && mouseX < bx + btnW && mouseY >= by && mouseY < by + btnH;
                    int bg = hov ? 0x80_4466aa : (enabled ? 0x60_333366 : 0x30_222244);
                    int bdr = hov ? 0xCC_6688cc : 0x60_555588;
                    graphics.fill(bx, by, bx + btnW, by + btnH, bg);
                    graphics.fill(bx, by, bx + btnW, by + 1, bdr);
                    graphics.fill(bx, by + btnH - 1, bx + btnW, by + btnH, bdr);
                    graphics.fill(bx, by, bx + 1, by + btnH, bdr);
                    graphics.fill(bx + btnW - 1, by, bx + btnW, by + btnH, bdr);
                    int tc = enabled ? (hov ? 0xFFFFFF : 0xCCCCCC) : 0x666666;
                    graphics.drawCenteredString(font, lbl, bx + btnW / 2, by + (btnH - font.lineHeight) / 2 + 1, tc);
                }
            }
        }

        @Override
        protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            if (entries.isEmpty()) return;

            int wH = getPickerWindowHeight();
            int startIdx = page * ITEMS_PER_PAGE;
            int endIdx = Math.min(startIdx + ITEMS_PER_PAGE, entries.size());

            // Tooltip for hovered item
            for (int i = startIdx; i < endIdx; i++) {
                int localIdx = i - startIdx;
                int col = localIdx % COLS;
                int row = localIdx / COLS;
                int ix = guiLeft + GRID_LEFT + col * CELL_SIZE;
                int iy = guiTop + GRID_TOP + row * CELL_SIZE;

                if (mouseX >= ix && mouseX < ix + CELL_SIZE && mouseY >= iy && mouseY < iy + CELL_SIZE) {
                    graphics.pose().pushPose();
                    graphics.pose().translate(0, 0, 600);
                    PickerEntry entry = entries.get(i);
                    Minecraft mc = Minecraft.getInstance();
                    List<Component> tooltip = new ArrayList<>(
                        entry.stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL));
                    tooltip.add(Component.literal(""));
                    if (entry.sceneIds.size() == 1) {
                        tooltip.add(Component.literal("Scene: " + entry.sceneIds.get(0))
                            .withStyle(ChatFormatting.AQUA));
                    } else {
                        tooltip.add(Component.literal(entry.sceneIds.size() + " scenes")
                            .withStyle(ChatFormatting.AQUA));
                        for (int j = 0; j < Math.min(entry.sceneIds.size(), 5); j++) {
                            tooltip.add(Component.literal("  " + entry.sceneIds.get(j))
                                .withStyle(ChatFormatting.DARK_AQUA));
                        }
                        if (entry.sceneIds.size() > 5) {
                            tooltip.add(Component.literal("  ...")
                                .withStyle(ChatFormatting.GRAY));
                        }
                    }
                    if (entry.nbtFilter != null) {
                        tooltip.add(Component.translatable("ponderer.ui.item_list.nbt_filter")
                            .withStyle(ChatFormatting.GOLD));
                        tooltip.add(Component.literal(entry.nbtFilter)
                            .withStyle(ChatFormatting.GRAY));
                    }
                    graphics.renderComponentTooltip(mc.font, tooltip, mouseX, mouseY);
                    graphics.pose().popPose();
                    break;
                }
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                // Pagination button handling
                if (totalPages > 1) {
                    int wH = getPickerWindowHeight();
                    int prevX = guiLeft + 10, btnY = guiTop + wH - 26, btnW = 20, btnH = 16;
                    int nextX = guiLeft + SEL_W - 30;
                    if (page > 0 && mouseX >= prevX && mouseX < prevX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
                        page--;
                        return true;
                    }
                    if (page < totalPages - 1 && mouseX >= nextX && mouseX < nextX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
                        page++;
                        return true;
                    }
                }

                // Item grid click handling
                if (!entries.isEmpty()) {
                    int startIdx = page * ITEMS_PER_PAGE;
                    int endIdx = Math.min(startIdx + ITEMS_PER_PAGE, entries.size());
                    for (int i = startIdx; i < endIdx; i++) {
                        int localIdx = i - startIdx;
                        int col = localIdx % COLS;
                        int row = localIdx / COLS;
                        int ix = guiLeft + GRID_LEFT + col * CELL_SIZE;
                        int iy = guiTop + GRID_TOP + row * CELL_SIZE;
                        if (mouseX >= ix && mouseX < ix + CELL_SIZE
                            && mouseY >= iy && mouseY < iy + CELL_SIZE) {
                            PickerEntry entry = entries.get(i);
                            if (entry.sceneIds.size() == 1) {
                                onSelect.accept(entry.sceneIds.get(0));
                            } else {
                                Minecraft.getInstance().setScreen(new SceneIdListScreen(
                                    entry.sceneIds, onSelect,
                                    () -> Minecraft.getInstance().setScreen(SceneItemPickerScreen.this)));
                            }
                            return true;
                        }
                    }
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void onClose() {
            onCancel.run();
        }

        @Override
        public boolean isPauseScreen() {
            return true;
        }
    }

    // -- Scene ID List (inner screen for multi-scene selection) --

    static class SceneIdListScreen extends AbstractSimiScreen {
        private static final int LIST_W = 220;
        private static final int ROW_HEIGHT = 16;
        private static final int VISIBLE_ROWS = 10;

        private final List<String> sceneIds;
        private final Consumer<String> onSelect;
        private final Runnable onCancel;
        private int scrollOffset = 0;

        SceneIdListScreen(List<String> sceneIds, Consumer<String> onSelect, Runnable onCancel) {
            super(Component.translatable("ponderer.ui.function_page.select_scene_id"));
            this.sceneIds = sceneIds;
            this.onSelect = onSelect;
            this.onCancel = onCancel;
        }

        private int getListWindowHeight() {
            int rows = Math.min(sceneIds.size(), VISIBLE_ROWS);
            return 36 + rows * ROW_HEIGHT + 10;
        }

        @Override
        protected void init() {
            setWindowSize(LIST_W, getListWindowHeight());
            super.init();
        }

        @Override
        protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            int wH = getListWindowHeight();

            new BoxElement()
                .withBackground(new Color(0xdd_000000, true))
                .gradientBorder(new Color(0x60_c0c0ff, true), new Color(0x30_c0c0ff, true))
                .at(guiLeft, guiTop, 0)
                .withBounds(LIST_W, wH)
                .render(graphics);

            var font = Minecraft.getInstance().font;

            graphics.drawCenteredString(font, this.title, guiLeft + LIST_W / 2, guiTop + 8, 0xFFFFFF);
            graphics.fill(guiLeft + 5, guiTop + 20, guiLeft + LIST_W - 5, guiTop + 21, 0x60_FFFFFF);

            int rows = Math.min(sceneIds.size(), VISIBLE_ROWS);
            for (int i = 0; i < rows; i++) {
                int idx = scrollOffset + i;
                if (idx >= sceneIds.size()) break;
                int rowY = guiTop + 26 + i * ROW_HEIGHT;
                boolean hovered = mouseX >= guiLeft + 6 && mouseX < guiLeft + LIST_W - 6
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
                if (hovered) {
                    graphics.fill(guiLeft + 6, rowY, guiLeft + LIST_W - 6, rowY + ROW_HEIGHT, 0x40_FFFFFF);
                }
                graphics.drawString(font, sceneIds.get(idx), guiLeft + 10, rowY + 4,
                    hovered ? 0x80FFFF : 0xCCCCCC);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                int rows = Math.min(sceneIds.size(), VISIBLE_ROWS);
                for (int i = 0; i < rows; i++) {
                    int idx = scrollOffset + i;
                    if (idx >= sceneIds.size()) break;
                    int rowY = guiTop + 26 + i * ROW_HEIGHT;
                    if (mouseX >= guiLeft + 6 && mouseX < guiLeft + LIST_W - 6
                        && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                        onSelect.accept(sceneIds.get(idx));
                        return true;
                    }
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            int maxScroll = Math.max(0, sceneIds.size() - VISIBLE_ROWS);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) scrollY));
            return true;
        }

        @Override
        public void onClose() {
            onCancel.run();
        }

        @Override
        public boolean isPauseScreen() {
            return true;
        }
    }
}
