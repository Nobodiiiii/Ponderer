package com.nododiiiii.ponderer.editor.screen;

import com.nododiiiii.ponderer.editor.capture.CoordPickState;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.runtime.api.SceneCatalog;
import com.nododiiiii.ponderer.runtime.core.SceneItemBindings;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.nbt.NbtTextCodec;
import net.createmod.catnip.gui.ConfirmationScreen;
import net.minecraft.client.Minecraft;
import com.nododiiiii.ponderer.editor.screen.widgets.DeclarativeFormEntry;
import com.nododiiiii.ponderer.editor.screen.widgets.ButtonListEntry;
import com.nododiiiii.ponderer.editor.screen.widgets.PondererGuiIcons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Editor screen for trigger settings: trigger mode, 3 parallel hint styles
 * (auto/title/subtitle each with independent frequency), and mode-specific fields.
 */
public class TriggerEditorScreen extends AbstractSceneEditorFormScreen {

    private static final String[] TRIGGER_MODES = {"none", "structure", "coordinate"};
    private static final String[] FREQ_VALUES = {null, "always", "first_time", "until_read"};
    private static final String[] FREQ_KEYS = {"off", "always", "first_time", "until_read"};

    private final KeyValueListState itemBindings = new KeyValueListState("itemBinding", 1);
    private final StepTextFieldHandle titleTextField = new StepTextFieldHandle("titleText");
    private final StepTextFieldHandle subtitleTextField = new StepTextFieldHandle("subtitleText");
    private final StepTextFieldHandle structureField = new StepTextFieldHandle("structure");
    private final StepXyzFieldHandle coord1Field = new StepXyzFieldHandle("coord1_x", "coord1_y", "coord1_z");
    private final StepXyzFieldHandle coord2Field = new StepXyzFieldHandle("coord2_x", "coord2_y", "coord2_z");

    private int triggerModeIndex = 0;
    private int autoFreqIndex = 0;
    private int titleFreqIndex = 0;
    private int subtitleFreqIndex = 0;
    private boolean pendingItemDuplicateConfirm = false;
    @Nullable
    private String pendingStructureSelection = null;

    private final FieldBinding<Integer> triggerModeBinding =
        FieldBindings.integer("triggerMode", () -> triggerModeIndex, value -> triggerModeIndex = value);
    private final FieldBinding<Integer> autoFreqBinding =
        FieldBindings.integer("autoFreq", () -> autoFreqIndex, value -> autoFreqIndex = value);
    private final FieldBinding<Integer> titleFreqBinding =
        FieldBindings.integer("titleFreq", () -> titleFreqIndex, value -> titleFreqIndex = value);
    private final FieldBinding<Integer> subtitleFreqBinding =
        FieldBindings.integer("subtitleFreq", () -> subtitleFreqIndex, value -> subtitleFreqIndex = value);

    public TriggerEditorScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(scene, sceneIndex, parent, "ponderer.ui.scope.editor", "ponderer.ui.step_editor",
            UILayoutConstants.EDITOR_LIST_W);

        if ("structure".equals(scene.triggerMode)) {
            triggerModeIndex = 1;
        } else if ("coordinate".equals(scene.triggerMode)) {
            triggerModeIndex = 2;
        }

        autoFreqIndex = freqToIndex(scene.hintAuto);
        titleFreqIndex = freqToIndex(scene.hintTitle);
        subtitleFreqIndex = freqToIndex(scene.hintSubtitle);

        if (autoFreqIndex == 0 && titleFreqIndex == 0 && subtitleFreqIndex == 0
            && scene.hintAuto == null && scene.hintTitle == null && scene.hintSubtitle == null
            && scene.hintStyle != null) {
            String legacyFreq = resolveLegacyFrequency(scene);
            if ("auto".equals(scene.hintStyle)) {
                autoFreqIndex = freqToIndex(legacyFreq);
            } else if ("title".equals(scene.hintStyle)) {
                titleFreqIndex = freqToIndex(legacyFreq);
            } else {
                subtitleFreqIndex = freqToIndex(legacyFreq);
            }
        }

        if (scene.items != null && !scene.items.isEmpty()) {
            List<KeyValueListState.KeyValue> bindings = new ArrayList<>();
            for (int i = 0; i < scene.items.size(); i++) {
                String nbt = SceneItemBindings.nbtFilter(scene, i);
                bindings.add(new KeyValueListState.KeyValue(scene.items.get(i), nbt != null ? nbt : ""));
            }
            itemBindings.replaceAll(bindings);
        }
        titleTextField.setValue(scene.hintTitleText != null ? scene.hintTitleText : "");
        subtitleTextField.setValue(scene.hintSubtitleText != null ? scene.hintSubtitleText : "");
        structureField.setValue(scene.triggerStructure != null ? scene.triggerStructure : "");

        if (scene.triggerCoord1 != null && scene.triggerCoord1.size() >= 3) {
            coord1Field.setValue(scene.triggerCoord1.get(0), scene.triggerCoord1.get(1), scene.triggerCoord1.get(2));
        } else {
            coord1Field.setValue(0, 0, 0);
        }
        if (scene.triggerCoord2 != null && scene.triggerCoord2.size() >= 3) {
            coord2Field.setValue(scene.triggerCoord2.get(0), scene.triggerCoord2.get(1), scene.triggerCoord2.get(2));
        } else {
            coord2Field.setValue(0, 0, 0);
        }
    }

    private static int freqToIndex(@Nullable String freq) {
        if ("always".equals(freq)) return 1;
        if ("first_time".equals(freq)) return 2;
        if ("until_read".equals(freq)) return 3;
        return 0;
    }

    private static String resolveLegacyFrequency(DslScene scene) {
        if ("first_time".equals(scene.hintFrequency)) return "first_time";
        if ("until_read".equals(scene.hintFrequency)) return "until_read";
        if (Boolean.TRUE.equals(scene.onlyFirstTime)) return "first_time";
        return "always";
    }

    @Override
    public TriggerEditorScreen setPendingFormRestore(@Nullable Map<String, String> snapshot) {
        super.setPendingFormRestore(snapshot);
        return this;
    }

    @Override
    protected void configureFormState(List<SnapshotParticipant> participants) {
        participants.add(triggerModeBinding);
        participants.add(autoFreqBinding);
        participants.add(titleFreqBinding);
        participants.add(subtitleFreqBinding);
        participants.add(itemBindings);
        participants.add(titleTextField);
        participants.add(subtitleTextField);
        participants.add(structureField);
        participants.add(coord1Field);
        participants.add(coord2Field);
    }

    @Override
    protected String getHeaderTitle() {
        return UIText.of("ponderer.ui.trigger_editor");
    }

    @Override
    protected void configureActionButtons() {
        if (confirmButton != null) {
            confirmButton.withCallback(this::doConfirm);
        }
    }

    @Override
    protected void collectFormEntries(List<DeclarativeFormEntry> entries) {
        if (pendingStructureSelection != null) {
            structureField.setValue(pendingStructureSelection);
            pendingStructureSelection = null;
        }

        collectItemBindingEntries(entries);
        entries.add(FieldSpecs.cycle(
            triggerModeBinding,
            "ponderer.ui.trigger_editor.trigger_mode",
            "ponderer.ui.trigger_editor.trigger_mode.tooltip",
            100,
            TRIGGER_MODES.length,
            this::rebuildFormPreservingState,
            () -> UIText.of("ponderer.ui.trigger_editor.trigger_mode." + TRIGGER_MODES[triggerModeIndex]),
            () -> 0xFFFFFF));

        if (triggerModeIndex == 0) {
            return;
        }

        entries.add(FieldSpecs.cycle(
            autoFreqBinding,
            "ponderer.ui.trigger_editor.hint_style.auto",
            "ponderer.ui.trigger_editor.hint_style.auto.tooltip",
            100,
            FREQ_VALUES.length,
            () -> {
            },
            () -> UIText.of("ponderer.ui.trigger_editor.hint_freq." + FREQ_KEYS[autoFreqIndex]),
            () -> autoFreqIndex != 0 ? 0xFF5555 : 0xFFFFFF));

        entries.add(FieldSpecs.cycle(
            titleFreqBinding,
            "ponderer.ui.trigger_editor.hint_style.title",
            "ponderer.ui.trigger_editor.hint_style.title.tooltip",
            100,
            FREQ_VALUES.length,
            this::rebuildFormPreservingState,
            () -> UIText.of("ponderer.ui.trigger_editor.hint_freq." + FREQ_KEYS[titleFreqIndex]),
            () -> 0xFFFFFF));
        if (titleFreqIndex != 0) {
            entries.add(FieldSpecs.text(
                titleTextField,
                "ponderer.ui.trigger_editor.hint_text",
                "ponderer.ui.trigger_editor.hint_text.tooltip",
                UIText.of("ponderer.ui.trigger_editor.hint_text.placeholder"),
                120));
        }

        entries.add(FieldSpecs.cycle(
            subtitleFreqBinding,
            "ponderer.ui.trigger_editor.hint_style.subtitle",
            "ponderer.ui.trigger_editor.hint_style.subtitle.tooltip",
            100,
            FREQ_VALUES.length,
            this::rebuildFormPreservingState,
            () -> UIText.of("ponderer.ui.trigger_editor.hint_freq." + FREQ_KEYS[subtitleFreqIndex]),
            () -> 0xFFFFFF));
        if (subtitleFreqIndex != 0) {
            entries.add(FieldSpecs.text(
                subtitleTextField,
                "ponderer.ui.trigger_editor.hint_text",
                "ponderer.ui.trigger_editor.hint_text.tooltip",
                UIText.of("ponderer.ui.trigger_editor.hint_text.placeholder"),
                120));
        }

        String mode = TRIGGER_MODES[triggerModeIndex];
        if ("structure".equals(mode)) {
            entries.add(FieldSpecs.typedText(
                FieldType.STRUCTURE,
                structureField,
                "ponderer.ui.trigger_editor.trigger_structure",
                null,
                UIText.of("ponderer.ui.trigger_editor.hint.trigger_structure"),
                124,
                FieldDecorators.iconAction(PondererGuiIcons.LIST,
                    UIText.of("ponderer.ui.scene_desc.structure_list_title"), this::openStructureList)));
        } else if ("coordinate".equals(mode)) {
            entries.add(FieldSpecs.xyz(
                coord1Field,
                "ponderer.ui.trigger_editor.trigger_coord1",
                null,
                FieldType.WORLD_POSITION,
                FieldDecorators.xyzIconAction(PondererGuiIcons.PICK_POSITION,
                    UIText.of("ponderer.ui.world_pick.tooltip"), this::startCoordinatePick)));
            entries.add(FieldSpecs.xyz(
                coord2Field,
                "ponderer.ui.trigger_editor.trigger_coord2",
                null,
                FieldType.WORLD_POSITION,
                FieldDecorators.xyzIconAction(PondererGuiIcons.PICK_POSITION,
                    UIText.of("ponderer.ui.world_pick.tooltip"), this::startCoordinatePick)));
        }
    }

    private void collectItemBindingEntries(List<DeclarativeFormEntry> entries) {
        for (int i = 0; i < itemBindings.size(); i++) {
            int index = i;
            String itemKey = "itemBinding_" + i + "_key";
            String nbtKey = "itemBinding_" + i + "_val";
            entries.add(FieldSpecs.text(
                FieldBindings.string(itemKey,
                    () -> index < itemBindings.size() ? itemBindings.get(index).key() : "",
                    value -> itemBindings.updateKey(index, value)),
                "ponderer.ui.scene_desc.item_id",
                "ponderer.ui.scene_desc.item_id.tooltip",
                UIText.of("ponderer.ui.scene_desc.hint.item_id"),
                124,
                FieldDecorators.jei(IdFieldMode.ITEM),
                FieldDecorators.itemTagDropdown(itemKey),
                FieldDecorators.heldItem(stack -> applyHeldItem(index, stack))));

            List<FieldDecorator> nbtDecorators = new ArrayList<>();
            if (itemBindings.size() > 1) {
                nbtDecorators.add(FieldDecorators.iconAction(PondererGuiIcons.REMOVE,
                    UIText.of("ponderer.ui.scene_desc.remove_item"), () -> {
                        itemBindings.remove(index);
                        rebuildFormPreservingState();
                    }));
            }
            entries.add(FieldSpecs.nbt(
                FieldBindings.string(nbtKey,
                    () -> index < itemBindings.size() ? itemBindings.get(index).value() : "",
                    value -> itemBindings.updateValue(index, value)),
                "ponderer.ui.scene_desc.item_nbt",
                "ponderer.ui.scene_desc.item_nbt.tooltip",
                124,
                nbtKey,
                nbtDecorators.toArray(FieldDecorator[]::new)));
        }
        entries.add(screen -> screen.appendBuiltEntry(new ButtonListEntry(
            "", null, 40, () -> {
                itemBindings.addEmpty();
                rebuildFormPreservingState();
            }, () -> "", () -> 0xFFFFFF, UIText.of("ponderer.ui.scene_desc.add_item"))
            .withIcon(PondererGuiIcons.ADD)));
    }

    private void applyHeldItem(int index, ItemStack stack) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        itemBindings.updateKey(index, itemId);
        itemBindings.updateValue(index, FieldDecorators.itemCustomData(stack));
        rebuildFormPreservingState();
    }

    private void openStructureList() {
        Minecraft.getInstance().setScreen(new StructureListScreen(this,
            selected -> pendingStructureSelection = selected, structureField.getValue()));
    }

    private void startCoordinatePick() {
        CoordPickState.startPick(snapshotForm(), new TriggerEditorContext(scene, sceneIndex, parent));
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    protected void restoreCustomSnapshot(Map<String, String> snapshot) {
        restoreNbtPickNotice(snapshot);
    }

    @Override
    protected void renderFormForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderFormForeground(graphics, mouseX, mouseY, partialTicks);
        if (triggerModeIndex != 0 && autoFreqIndex != 0) {
            String warning = UIText.of("ponderer.ui.trigger_editor.auto_warning");
            int warnY = height - 58;
            graphics.drawCenteredString(Minecraft.getInstance().font, warning,
                width / 2, warnY, 0xFF5555);
        }
    }

    @Override
    protected boolean saveEdits() {
        return doSave();
    }

    @Override
    protected SnapshotReturnContext createReturnContext() {
        return new TriggerEditorContext(scene, sceneIndex, parent);
    }

    private void doConfirm() {
        if (!doSave()) {
            return;
        }
        returnToParent();
    }

    private boolean doSave() {
        clearStatusMessages();

        List<String> newItems = new ArrayList<>();
        List<String> newFilters = new ArrayList<>();
        for (KeyValueListState.KeyValue binding : itemBindings.items()) {
            String item = binding.key().trim();
            if (item.isEmpty()) {
                setErrorMessage(UIText.of("ponderer.ui.scene_desc.empty_item"));
                return false;
            }
            ResourceLocation bindingId = SceneItemBindings.id(item);
            if (bindingId == null) {
                setErrorMessage(UIText.of("ponderer.cmd.new.error.invalid_binding", item));
                return false;
            }
            item = (SceneItemBindings.isTag(item) ? "#" : "") + bindingId;
            if ((scene.items == null || !scene.items.contains(item))
                && SceneItemBindings.resolve(List.of(item)).isEmpty()) {
                setErrorMessage(UIText.of("ponderer.cmd.new.error.empty_binding", item));
                return false;
            }
            String nbt = binding.value().trim();
            if (!nbt.isEmpty()) {
                NbtTextCodec.ParseResult parsed = NbtTextCodec.parse(nbt);
                if (!parsed.success()) {
                    setErrorMessage(UIText.of("ponderer.ui.nbt_editor.error.invalid"));
                    return false;
                }
                nbt = NbtTextCodec.compact(parsed.tag());
            }
            newItems.add(item);
            newFilters.add(nbt);
        }

        if (!pendingItemDuplicateConfirm) {
            List<String> duplicates = newItems.stream().distinct()
                .filter(item -> scene.items == null || !scene.items.contains(item))
                .filter(item -> com.nododiiiii.ponderer.bootstrap.ProductRuntime.engine().catalog().getScenes().stream()
                    .anyMatch(s -> s != scene && s.items != null && s.items.contains(item)))
                .toList();
            if (!duplicates.isEmpty()) {
                new ConfirmationScreen()
                    .centered()
                    .withText(Component.translatable("ponderer.ui.scene_desc.error.item_exists_title"))
                    .addText(Component.translatable("ponderer.ui.scene_desc.error.item_exists", String.join(", ", duplicates)))
                    .withAction(confirmed -> {
                        if (confirmed) {
                            pendingItemDuplicateConfirm = true;
                            doConfirm();
                        }
                    })
                    .open(this);
                return false;
            }
        }
        pendingItemDuplicateConfirm = false;

        DslScene candidate = SceneStore.copyScene(scene);
        if (candidate == null) {
            setErrorMessage(UIText.of("ponderer.ui.save_error.io", "Unable to prepare scene copy"));
            return false;
        }

        candidate.items = List.copyOf(newItems);
        String firstFilter = newFilters.getFirst();
        boolean sharedFilter = newFilters.stream().allMatch(firstFilter::equals);
        candidate.nbtFilter = sharedFilter ? emptyToNull(firstFilter) : null;
        candidate.itemNbtFilters = sharedFilter ? null : List.copyOf(newFilters);

        String triggerMode = TRIGGER_MODES[triggerModeIndex];
        if ("structure".equals(triggerMode)) {
            String structId = structureField.getValue().trim();
            if (!structId.isEmpty() && !isValidStructureId(structId)) {
                setErrorMessage(UIText.of("ponderer.ui.trigger_editor.error.invalid_structure"));
                return false;
            }
        }

        candidate.triggerMode = "none".equals(triggerMode) ? null : triggerMode;
        candidate.hintAuto = FREQ_VALUES[autoFreqIndex];
        candidate.hintTitle = FREQ_VALUES[titleFreqIndex];
        candidate.hintSubtitle = FREQ_VALUES[subtitleFreqIndex];
        candidate.hintTitleText = titleFreqIndex != 0 ? emptyToNull(titleTextField.getValue()) : null;
        candidate.hintSubtitleText = subtitleFreqIndex != 0 ? emptyToNull(subtitleTextField.getValue()) : null;

        candidate.hintStyle = null;
        candidate.hintFrequency = null;
        candidate.onlyFirstTime = null;

        if ("structure".equals(triggerMode)) {
            candidate.triggerStructure = emptyToNull(structureField.getValue());
            candidate.triggerStructureRange = null;
            candidate.triggerCoord1 = null;
            candidate.triggerCoord2 = null;
        } else if ("coordinate".equals(triggerMode)) {
            Integer x1 = parseBlockCoordinate(coord1Field.x().isBlank() ? "0" : coord1Field.x(), "X1");
            Integer y1 = parseBlockCoordinate(coord1Field.y().isBlank() ? "0" : coord1Field.y(), "Y1");
            Integer z1 = parseBlockCoordinate(coord1Field.z().isBlank() ? "0" : coord1Field.z(), "Z1");
            Integer x2 = parseBlockCoordinate(coord2Field.x().isBlank() ? "0" : coord2Field.x(), "X2");
            Integer y2 = parseBlockCoordinate(coord2Field.y().isBlank() ? "0" : coord2Field.y(), "Y2");
            Integer z2 = parseBlockCoordinate(coord2Field.z().isBlank() ? "0" : coord2Field.z(), "Z2");
            if (x1 == null || y1 == null || z1 == null || x2 == null || y2 == null || z2 == null) return false;
            candidate.triggerCoord1 = List.of(
                x1, y1, z1);
            candidate.triggerCoord2 = List.of(
                x2, y2, z2);
            candidate.triggerStructure = null;
            candidate.triggerStructureRange = null;
        } else {
            candidate.triggerStructure = null;
            candidate.triggerStructureRange = null;
            candidate.triggerCoord1 = null;
            candidate.triggerCoord2 = null;
        }

        com.nododiiiii.ponderer.workspace.api.WorkspaceResults.LocalSaveResult saveResult = SceneStore.saveSceneToLocalDetailed(candidate);
        if (!saveResult.isSuccess()) {
            setErrorMessage(UIText.saveError(saveResult));
            return false;
        }

        scene.items = candidate.items;
        scene.nbtFilter = candidate.nbtFilter;
        scene.itemNbtFilters = candidate.itemNbtFilters;
        scene.triggerMode = candidate.triggerMode;
        scene.hintAuto = candidate.hintAuto;
        scene.hintTitle = candidate.hintTitle;
        scene.hintSubtitle = candidate.hintSubtitle;
        scene.hintTitleText = candidate.hintTitleText;
        scene.hintSubtitleText = candidate.hintSubtitleText;
        scene.hintStyle = candidate.hintStyle;
        scene.hintFrequency = candidate.hintFrequency;
        scene.onlyFirstTime = candidate.onlyFirstTime;
        scene.triggerStructure = candidate.triggerStructure;
        scene.triggerStructureRange = candidate.triggerStructureRange;
        scene.triggerCoord1 = candidate.triggerCoord1;
        scene.triggerCoord2 = candidate.triggerCoord2;

        SceneStore.reloadFromDisk();
        Minecraft.getInstance().execute(com.nododiiiii.ponderer.application.SceneRefreshCoordinator::reloadPonderIndex);
        return true;
    }

    private boolean isValidStructureId(String structId) {
        ResourceLocation loc = ResourceLocation.tryParse(structId);
        if (loc == null) return false;
        try {
            MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
            if (server != null) {
                return server.registryAccess().registryOrThrow(Registries.STRUCTURE).containsKey(loc);
            }
            var connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                return connection.registryAccess().registryOrThrow(Registries.STRUCTURE).containsKey(loc);
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    @Nullable
    private static String emptyToNull(@Nullable String s) {
        return s != null && !s.trim().isEmpty() ? s.trim() : null;
    }
}
