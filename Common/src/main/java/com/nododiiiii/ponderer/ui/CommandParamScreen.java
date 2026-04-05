package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeFormScreen;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
import com.nododiiiii.ponderer.ui.catnip.FormTextButtonSpec;
import com.nododiiiii.ponderer.ui.catnip.FormEntries;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CommandParamScreen extends AbstractDeclarativeFormScreen implements JeiTextButtonHost {

    public sealed interface FieldDef permits TextFieldDef, ChoiceFieldDef, ToggleFieldDef {
    }

    public record TextFieldDef(String id, String labelKey, String hintKey, boolean required,
                               @Nullable IdFieldMode jeiMode,
                               boolean sceneSelector, boolean sceneMultiSelect) implements FieldDef {
    }

    public record ChoiceFieldDef(String id, String labelKey, List<String> optionLabelKeys,
                                 List<String> values) implements FieldDef {
    }

    public record ToggleFieldDef(String id, String labelKey, boolean defaultValue) implements FieldDef {
    }

    private final List<FieldDef> fieldDefs;
    private final Consumer<Map<String, String>> onExecute;
    private final String titleKey;

    private final Map<String, String> textValues = new LinkedHashMap<>();
    private final Map<String, Integer> choiceSelections = new HashMap<>();
    private final Map<String, Boolean> toggleStates = new HashMap<>();
    private final Map<String, HintableTextFieldWidget> textInputs = new LinkedHashMap<>();
    private final Map<String, String> defaultValues = new HashMap<>();

    private final Map<String, String> toggleDependencies = new HashMap<>();
    private final Map<String, String> fieldDisablesToggle = new HashMap<>();
    private final Map<String, Map<String, Supplier<String>>> toggleAutoFill = new HashMap<>();

    private boolean suppressFieldResponder = false;
    private boolean collectingEntries = false;
    private boolean initialSnapshotCaptured = false;
    private final FormState formState = new FormState(this::snapshotState, this::restoreSnapshot);
    private final JeiFieldController jeiController = new JeiFieldController(JeiCompat::setActiveScreen);

    private CommandParamScreen(String titleKey, List<FieldDef> fieldDefs, Consumer<Map<String, String>> onExecute) {
        super(new FunctionScreen(), "ponderer.ui.scope.editor", titleKey, UILayoutConstants.EDITOR_LIST_W);
        this.titleKey = titleKey;
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

    @Override
    protected void init() {
        super.init();
        if (!initialSnapshotCaptured) {
            formState.captureBaseline();
            initialSnapshotCaptured = true;
        }
    }

    @Override
    protected void collectFormEntries(List<DeclarativeFormEntry> entries) {
        textInputs.clear();
        collectingEntries = true;

        try {
            for (FieldDef def : fieldDefs) {
                if (def instanceof TextFieldDef textDef) {
                    entries.add(FormEntries.text(
                        textDef.labelKey,
                        null,
                        textDef.hintKey,
                        "",
                        value -> handleTextChanged(textDef.id, value),
                        entry -> {
                            entry.field().setMaxLength(32500);
                            textInputs.put(textDef.id, entry.field());
                            suppressFieldResponder = true;
                            entry.field().setValue(currentTextValue(textDef.id));
                            suppressFieldResponder = false;
                        },
                        textButtonsFor(textDef)));
                    continue;
                }

                if (def instanceof ChoiceFieldDef choiceDef) {
                    ensureChoiceState(choiceDef);
                    entries.add(FormEntries.choice(
                        choiceDef.labelKey,
                        null,
                        140,
                        () -> cycleChoice(choiceDef),
                        () -> UIText.of(choiceDef.optionLabelKeys.get(choiceSelections.getOrDefault(choiceDef.id, 0))),
                        () -> 0xFFFFFF,
                        null));
                    continue;
                }

                ToggleFieldDef toggleDef = (ToggleFieldDef) def;
                toggleStates.putIfAbsent(toggleDef.id, toggleDef.defaultValue);
                entries.add(FormEntries.toggle(
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
    protected boolean hasUnsavedChanges() {
        return initialSnapshotCaptured && formState.hasUnsavedChanges();
    }

    @Override
    protected int getUnsavedChangeCount() {
        return initialSnapshotCaptured ? formState.dirtyCount() : 0;
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

        if (jeiController.isActive()) {
            deactivateJei();
        }
        Minecraft.getInstance().setScreen(null);
        onExecute.accept(values);
        return true;
    }

    @Override
    protected void discardEdits() {
        clearStatusMessages();
        formState.restoreBaseline();
        rebuildEntries(currentListScroll());
    }

    @Override
    public void removed() {
        super.removed();
        if (jeiController.isActive()) {
            deactivateJei();
        }
    }

    @Override
    @Nullable
    public HintableTextFieldWidget getJeiTargetField() {
        return jeiController.targetField();
    }

    @Override
    public void toggleJeiForField(HintableTextFieldWidget field, IdFieldMode mode) {
        jeiController.toggle(this, field, mode);
        rebuildEntries(currentListScroll());
    }

    @Override
    public boolean isJeiActiveForField(HintableTextFieldWidget field) {
        return jeiController.isActiveFor(field);
    }

    @Override
    public void deactivateJei() {
        jeiController.deactivate();
    }

    @Override
    public void showJeiIncompatibleWarning(IdFieldMode mode) {
        setErrorMessage(switch (mode) {
            case BLOCK -> UIText.of("ponderer.ui.jei.error.not_block");
            case ENTITY -> UIText.of("ponderer.ui.jei.error.not_spawn_egg");
            case ITEM, INGREDIENT -> null;
        });
    }

    @Override
    public int getGuiLeft() {
        return width / 2 - currentListWidthValue() / 2 - 40;
    }

    @Override
    public int getGuiTop() {
        return 35;
    }

    @Override
    public int getGuiWidth() {
        return currentListWidthValue() + 80;
    }

    @Override
    public int getGuiHeight() {
        return height - 60;
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
        rebuildEntries(currentListScroll());
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
        rebuildEntries(currentListScroll());
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

    private void cycleChoice(ChoiceFieldDef choiceDef) {
        ensureChoiceState(choiceDef);
        int next = (choiceSelections.get(choiceDef.id) + 1) % choiceDef.values.size();
        choiceSelections.put(choiceDef.id, next);
        clearStatusMessages();
        rebuildEntries(currentListScroll());
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

    private void restoreSnapshot(Map<String, String> snapshot) {
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

    private Map<String, String> snapshotState() {
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

    private String currentTextValue(String fieldId) {
        return textValues.getOrDefault(fieldId, defaultValues.getOrDefault(fieldId, ""));
    }

    private FormTextButtonSpec[] textButtonsFor(TextFieldDef textDef) {
        List<FormTextButtonSpec> specs = new ArrayList<>();
        if (textDef.jeiMode != null) {
            specs.add(FormTextButtonSpec.jei(textDef.jeiMode));
        }
        if (textDef.sceneSelector) {
            specs.add(FormTextButtonSpec.action(
                "S",
                0x80FFFF,
                null,
                () -> openSceneSelector(textDef.id, textDef.sceneMultiSelect)));
        }
        return specs.toArray(FormTextButtonSpec[]::new);
    }

    private void setTextValue(String fieldId, @Nullable String value) {
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

        public Builder sceneIdField(String id, String labelKey, String hintKey, boolean required,
                                    boolean multiSelect) {
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
            return new CommandParamScreen(titleKey, List.copyOf(fields), onExecute);
        }
    }
}
