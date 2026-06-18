package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.PondererClientCommands;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CommandParamScreen extends AbstractJeiAwareFormScreen {

    private static final int CHOICE_BUTTON_WIDTH = 100;

    public sealed interface FieldDef permits TextFieldDef, ChoiceFieldDef, ToggleFieldDef {
    }

    public record TextFieldDef(String id, String labelKey, @Nullable String hintKey, boolean required,
                               @Nullable IdFieldMode jeiMode,
                               boolean sceneSelector, boolean sceneMultiSelect,
                               boolean packSelector) implements FieldDef {
    }

    public record ChoiceFieldDef(String id, String labelKey, List<String> optionLabelKeys,
                                 List<String> values) implements FieldDef {
    }

    public record ToggleFieldDef(String id, String labelKey, boolean defaultValue) implements FieldDef {
    }

    private record VisibilityRule(String controllerFieldId, String expectedValue) {
    }

    private final List<FieldDef> fieldDefs;
    private final Consumer<Map<String, String>> onExecute;

    private final Map<String, String> textValues = new LinkedHashMap<>();
    private final Map<String, Integer> choiceSelections = new HashMap<>();
    private final Map<String, Boolean> toggleStates = new HashMap<>();
    private final Map<String, HintableTextFieldWidget> textInputs = new LinkedHashMap<>();
    private final Map<String, String> defaultValues = new HashMap<>();

    private final Map<String, String> toggleDependencies = new HashMap<>();
    private final Map<String, String> fieldDisablesToggle = new HashMap<>();
    private final Map<String, Map<String, Supplier<String>>> toggleAutoFill = new HashMap<>();
    private final Map<String, VisibilityRule> fieldVisibility = new HashMap<>();

    private boolean suppressFieldResponder = false;
    private boolean collectingEntries = false;

    private CommandParamScreen(String titleKey, List<FieldDef> fieldDefs, Consumer<Map<String, String>> onExecute) {
        super(new FunctionScreen(), "ponderer.ui.scope.editor", titleKey, UILayoutConstants.EDITOR_LIST_W,
            JeiCompat::setActiveScreen);
        this.fieldDefs = fieldDefs;
        this.onExecute = onExecute;
    }

    public static Builder builder(String titleKey) {
        return new Builder(titleKey);
    }

    public void setDefaultValue(String fieldId, String value) {
        defaultValues.put(fieldId, value);
    }

    public void addToggleDependency(String childToggleId, String parentToggleId) {
        toggleDependencies.put(childToggleId, parentToggleId);
    }

    public void addFieldDisablesToggle(String fieldId, String toggleId) {
        fieldDisablesToggle.put(fieldId, toggleId);
    }

    public void addToggleAutoFill(String toggleId, String fieldId, Supplier<String> valueSupplier) {
        toggleAutoFill.computeIfAbsent(toggleId, key -> new HashMap<>()).put(fieldId, valueSupplier);
    }

    public void showFieldWhenValue(String fieldId, String controllerFieldId, String expectedValue) {
        fieldVisibility.put(fieldId, new VisibilityRule(controllerFieldId, expectedValue));
    }

    @Override
    protected void collectFormEntries(List<DeclarativeFormEntry> entries) {
        textInputs.clear();
        collectingEntries = true;

        try {
            for (FieldDef def : fieldDefs) {
                if (!isFieldVisible(def)) {
                    continue;
                }

                if (def instanceof TextFieldDef textDef) {
                    if (textDef.sceneSelector) {
                        entries.add(FieldSpecs.labeledButton(
                            textDef.labelKey,
                            textDef.hintKey,
                            () -> openSceneSelector(textDef.id, textDef.sceneMultiSelect),
                            () -> currentSceneSelectionButtonLabel(textDef),
                            sceneSelectorTooltipGetter(textDef)));
                        continue;
                    }
                    if (textDef.packSelector) {
                        entries.add(FieldSpecs.labeledButton(
                            textDef.labelKey,
                            textDef.hintKey,
                            () -> openPackSelector(textDef.id),
                            () -> currentPackSelectionButtonLabel(textDef),
                            packSelectorTooltipGetter(textDef)));
                        continue;
                    }

                    entries.add(FieldSpecs.text(
                        FieldBindings.transientString(
                            () -> currentTextValue(textDef.id),
                            value -> handleTextChanged(textDef.id, value)),
                        textDef.labelKey,
                        null,
                        textDef.hintKey,
                        -1,
                        entry -> {
                            entry.field().setMaxLength(32500);
                            textInputs.put(textDef.id, entry.field());
                        },
                        textDecoratorsFor(textDef)));
                    continue;
                }

                if (def instanceof ChoiceFieldDef choiceDef) {
                    ensureChoiceState(choiceDef);
                    entries.add(FieldSpecs.choice(
                        choiceDef.labelKey,
                        null,
                        CHOICE_BUTTON_WIDTH,
                        () -> cycleChoice(choiceDef),
                        () -> UIText.of(choiceDef.optionLabelKeys.get(choiceSelections.getOrDefault(choiceDef.id, 0))),
                        () -> 0xFFFFFF,
                        null));
                    continue;
                }

                ToggleFieldDef toggleDef = (ToggleFieldDef) def;
                toggleStates.putIfAbsent(toggleDef.id, toggleDef.defaultValue);
                entries.add(FieldSpecs.toggle(
                    toggleDef.labelKey,
                    null,
                    () -> toggleStates.getOrDefault(toggleDef.id, toggleDef.defaultValue),
                    () -> handleToggle(toggleDef.id)));
            }
        } finally {
            collectingEntries = false;
        }
    }

    @Override
    protected boolean saveEdits() {
        clearStatusMessages();
        Map<String, String> values = new HashMap<>();

        for (FieldDef def : fieldDefs) {
            if (def instanceof TextFieldDef textDef) {
                String value = currentTextValue(textDef.id).trim();
                if (textDef.required && value.isEmpty()) {
                    setErrorMessage(UIText.of("ponderer.ui.error.required_field", UIText.of(textDef.labelKey)));
                    return false;
                }
                values.put(textDef.id, value);
                continue;
            }
            if (def instanceof ChoiceFieldDef choiceDef) {
                ensureChoiceState(choiceDef);
                values.put(choiceDef.id, choiceDef.values.get(choiceSelections.get(choiceDef.id)));
                continue;
            }
            ToggleFieldDef toggleDef = (ToggleFieldDef) def;
            values.put(toggleDef.id, String.valueOf(toggleStates.getOrDefault(toggleDef.id, toggleDef.defaultValue)));
        }

        deactivateJei();
        PondererClientCommands.runReportingTo(
            this::setInfoMessage,
            this::setErrorMessage,
            () -> onExecute.accept(values));
        markStateSaved();
        return true;
    }

    @Override
    protected void prepareSnapshotForBuild(Map<String, String> snapshot) {
        restoreSnapshot(snapshot);
    }

    @Override
    protected boolean rebuildOnJeiStateChange() {
        return true;
    }

    @Override
    protected boolean isSaveButtonActive() {
        return true;
    }

    @Override
    protected void restoreSnapshot(Map<String, String> snapshot) {
        for (FieldDef def : fieldDefs) {
            if (def instanceof TextFieldDef textDef) {
                textValues.put(textDef.id, snapshot.getOrDefault(textDef.id, defaultValues.getOrDefault(textDef.id, "")));
                continue;
            }
            if (def instanceof ChoiceFieldDef choiceDef) {
                String targetValue = snapshot.get(choiceDef.id);
                int index = targetValue == null ? 0 : choiceDef.values.indexOf(targetValue);
                choiceSelections.put(choiceDef.id, Math.max(0, index));
                continue;
            }
            ToggleFieldDef toggleDef = (ToggleFieldDef) def;
            boolean state = Boolean.parseBoolean(snapshot.getOrDefault(toggleDef.id, String.valueOf(toggleDef.defaultValue)));
            toggleStates.put(toggleDef.id, state);
        }
    }

    @Override
    protected Map<String, String> snapshotState() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (FieldDef def : fieldDefs) {
            if (def instanceof TextFieldDef textDef) {
                snapshot.put(textDef.id, currentTextValue(textDef.id));
                continue;
            }
            if (def instanceof ChoiceFieldDef choiceDef) {
                ensureChoiceState(choiceDef);
                snapshot.put(choiceDef.id, choiceDef.values.get(choiceSelections.get(choiceDef.id)));
                continue;
            }
            ToggleFieldDef toggleDef = (ToggleFieldDef) def;
            snapshot.put(toggleDef.id, String.valueOf(toggleStates.getOrDefault(toggleDef.id, toggleDef.defaultValue)));
        }
        return snapshot;
    }

    private void handleTextChanged(String fieldId, String value) {
        textValues.put(fieldId, value);
        clearStatusMessages();
        if (suppressFieldResponder) {
            return;
        }

        String toggleToDisable = fieldDisablesToggle.get(fieldId);
        if (toggleToDisable == null || collectingEntries) {
            return;
        }
        toggleStates.put(toggleToDisable, false);
        for (var dependency : toggleDependencies.entrySet()) {
            if (dependency.getValue().equals(toggleToDisable)) {
                toggleStates.put(dependency.getKey(), false);
            }
        }
        rebuildListPreservingScroll();
    }

    private void handleToggle(String id) {
        boolean newState = !toggleStates.getOrDefault(id, false);
        String parent = toggleDependencies.get(id);
        if (parent != null && newState && !toggleStates.getOrDefault(parent, false)) {
            return;
        }

        toggleStates.put(id, newState);
        if (newState) {
            Map<String, Supplier<String>> fills = toggleAutoFill.get(id);
            if (fills != null) {
                for (var fill : fills.entrySet()) {
                    setTextValue(fill.getKey(), fill.getValue().get());
                }
            }
        } else {
            for (var dependency : toggleDependencies.entrySet()) {
                if (dependency.getValue().equals(id)) {
                    toggleStates.put(dependency.getKey(), false);
                }
            }
        }

        clearStatusMessages();
        rebuildListPreservingScroll();
    }

    private void openSceneSelector(String targetFieldId, boolean multiSelect) {
        List<DslScene> scenes = SceneRuntime.getScenes();
        if (scenes.isEmpty()) {
            setErrorMessage(UIText.of("ponderer.ui.function_page.no_scenes"));
            return;
        }

        if (multiSelect) {
            Minecraft.getInstance().setScreen(new PonderItemGridScreen(
                selectedIds -> {
                    setTextValue(targetFieldId, String.join(",", selectedIds));
                    Minecraft.getInstance().setScreen(this);
                },
                () -> Minecraft.getInstance().setScreen(this),
                true));
            return;
        }

        Minecraft.getInstance().setScreen(new PonderItemGridScreen(
            sceneId -> {
                setTextValue(targetFieldId, sceneId);
                Minecraft.getInstance().setScreen(this);
            },
            () -> Minecraft.getInstance().setScreen(this)));
    }

    private void openPackSelector(String targetFieldId) {
        Minecraft.getInstance().setScreen(new PonderPackSelectScreen(
            this,
            packId -> {
                setTextValue(targetFieldId, packId);
                Minecraft.getInstance().setScreen(this);
            },
            () -> Minecraft.getInstance().setScreen(this)));
    }

    private void cycleChoice(ChoiceFieldDef choiceDef) {
        ensureChoiceState(choiceDef);
        int next = (choiceSelections.get(choiceDef.id) + 1) % choiceDef.values.size();
        choiceSelections.put(choiceDef.id, next);
        clearStatusMessages();
        rebuildListPreservingScroll();
    }

    private void ensureChoiceState(ChoiceFieldDef choiceDef) {
        choiceSelections.computeIfAbsent(choiceDef.id, ignored -> {
            String baselineValue = defaultValues.get(choiceDef.id);
            if (baselineValue != null) {
                int index = choiceDef.values.indexOf(baselineValue);
                if (index >= 0) {
                    return index;
                }
            }
            return 0;
        });
    }

    private String currentTextValue(String fieldId) {
        return textValues.getOrDefault(fieldId, defaultValues.getOrDefault(fieldId, ""));
    }

    private String currentSceneSelectionButtonLabel(TextFieldDef textDef) {
        int selectedCount = selectedSceneCount(currentTextValue(textDef.id));
        if (selectedCount <= 0) {
            return UIText.of(textDef.sceneMultiSelect
                ? "ponderer.ui.export.select_scenes"
                : "ponderer.ui.function_page.select_scene");
        }
        return UIText.of("ponderer.ui.export.selected_scenes", selectedCount);
    }

    private String currentPackSelectionButtonLabel(TextFieldDef textDef) {
        String pack = currentTextValue(textDef.id).trim();
        if (pack.isEmpty()) {
            return UIText.of("ponderer.ui.function_page.select_pack");
        }
        return UIText.of("ponderer.ui.function_page.selected_pack", pack);
    }

    private int selectedSceneCount(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return 0;
        }

        int count = 0;
        for (String part : rawValue.split(",")) {
            if (!part.trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    @Nullable
    private Supplier<String> sceneSelectorTooltipGetter(TextFieldDef textDef) {
        return () -> {
            int selectedCount = selectedSceneCount(currentTextValue(textDef.id));
            if (selectedCount > 0) {
                return UIText.of("ponderer.ui.export.selected_scenes", selectedCount);
            }
            if (textDef.hintKey == null || textDef.hintKey.isBlank()) {
                return "";
            }
            return UIText.of(textDef.hintKey);
        };
    }

    @Nullable
    private Supplier<String> packSelectorTooltipGetter(TextFieldDef textDef) {
        return () -> {
            String pack = currentTextValue(textDef.id).trim();
            if (!pack.isEmpty()) {
                return UIText.of("ponderer.ui.function_page.selected_pack", pack);
            }
            if (textDef.hintKey == null || textDef.hintKey.isBlank()) {
                return "";
            }
            return UIText.of(textDef.hintKey);
        };
    }

    private FieldDecorator[] textDecoratorsFor(TextFieldDef textDef) {
        List<FieldDecorator> specs = new ArrayList<>();
        if (textDef.jeiMode != null) {
            specs.add(FieldDecorators.jei(textDef.jeiMode));
        }
        if (textDef.sceneSelector) {
            specs.add(FieldDecorators.sceneSelector(() -> openSceneSelector(textDef.id, textDef.sceneMultiSelect)));
        }
        return specs.toArray(FieldDecorator[]::new);
    }

    private boolean isFieldVisible(FieldDef def) {
        VisibilityRule rule = fieldVisibility.get(fieldId(def));
        if (rule == null) {
            return true;
        }
        return rule.expectedValue().equals(currentFieldValue(rule.controllerFieldId()));
    }

    private String currentFieldValue(String fieldId) {
        FieldDef def = findFieldDef(fieldId);
        if (def instanceof TextFieldDef textDef) {
            return currentTextValue(textDef.id);
        }
        if (def instanceof ChoiceFieldDef choiceDef) {
            ensureChoiceState(choiceDef);
            return choiceDef.values.get(choiceSelections.get(choiceDef.id));
        }
        if (def instanceof ToggleFieldDef toggleDef) {
            return String.valueOf(toggleStates.getOrDefault(toggleDef.id, toggleDef.defaultValue));
        }
        return "";
    }

    private FieldDef findFieldDef(String fieldId) {
        for (FieldDef def : fieldDefs) {
            if (fieldId(def).equals(fieldId)) {
                return def;
            }
        }
        throw new IllegalArgumentException("Unknown field id: " + fieldId);
    }

    private static String fieldId(FieldDef def) {
        if (def instanceof TextFieldDef textDef) {
            return textDef.id;
        }
        if (def instanceof ChoiceFieldDef choiceDef) {
            return choiceDef.id;
        }
        return ((ToggleFieldDef) def).id;
    }

    private void setTextValue(String fieldId, @Nullable String value) {
        clearStatusMessages();
        textValues.put(fieldId, value == null ? "" : value);
        HintableTextFieldWidget field = textInputs.get(fieldId);
        if (field != null) {
            suppressFieldResponder = true;
            field.setValue(value == null ? "" : value);
            suppressFieldResponder = false;
        }
    }

    public static class Builder {
        private final String titleKey;
        private final List<FieldDef> fields = new ArrayList<>();
        private Consumer<Map<String, String>> onExecute = ignored -> {
        };

        private Builder(String titleKey) {
            this.titleKey = titleKey;
        }

        public Builder textField(String id, String labelKey, String hintKey, boolean required) {
            fields.add(new TextFieldDef(id, labelKey, hintKey, required, null, false, false, false));
            return this;
        }

        public Builder itemField(String id, String labelKey, String hintKey, boolean required) {
            fields.add(new TextFieldDef(id, labelKey, hintKey, required, IdFieldMode.ITEM, false, false, false));
            return this;
        }

        public Builder sceneIdField(String id, String labelKey, @Nullable String hintKey, boolean required) {
            fields.add(new TextFieldDef(id, labelKey, hintKey, required, null, true, false, false));
            return this;
        }

        public Builder sceneIdField(String id, String labelKey, @Nullable String hintKey, boolean required,
                                    boolean multiSelect) {
            fields.add(new TextFieldDef(id, labelKey, hintKey, required, null, true, multiSelect, false));
            return this;
        }

        public Builder packIdField(String id, String labelKey, @Nullable String hintKey, boolean required) {
            fields.add(new TextFieldDef(id, labelKey, hintKey, required, null, false, false, true));
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
            return new CommandParamScreen(titleKey, List.copyOf(fields), onExecute);
        }
    }
}
