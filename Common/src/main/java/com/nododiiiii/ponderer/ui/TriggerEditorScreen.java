package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import com.nododiiiii.ponderer.ponder.SceneStore;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Editor screen for trigger settings: trigger mode, 3 parallel hint styles
 * (auto/title/subtitle each with independent frequency), and mode-specific fields.
 */
public class TriggerEditorScreen extends AbstractStepEditorScreen {

    private static final String[] TRIGGER_MODES = {"none", "structure", "coordinate"};
    /** Frequency options: 0=off, 1=always, 2=first_time, 3=until_read */
    private static final String[] FREQ_VALUES = {null, "always", "first_time", "until_read"};
    private static final String[] FREQ_KEYS = {"off", "always", "first_time", "until_read"};

    private final StepTextFieldHandle itemField = new StepTextFieldHandle("itemId");
    private final StepTextFieldHandle itemNbtField = new StepTextFieldHandle("itemNbt");
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

    public TriggerEditorScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.trigger_editor"), scene, sceneIndex, parent);

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

        String currentItem = scene.items != null && !scene.items.isEmpty() ? scene.items.get(0) : "";
        itemField.setValue(currentItem);
        itemNbtField.setValue(scene.nbtFilter != null ? scene.nbtFilter : "");
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

    public void setPendingFormRestore(Map<String, String> snapshot) {
        if (snapshot.containsKey("triggerMode")) {
            try {
                triggerModeIndex = Integer.parseInt(snapshot.get("triggerMode"));
            } catch (NumberFormatException ignored) {
            }
        }
        if (snapshot.containsKey("autoFreq")) {
            try {
                autoFreqIndex = Integer.parseInt(snapshot.get("autoFreq"));
            } catch (NumberFormatException ignored) {
            }
        }
        if (snapshot.containsKey("titleFreq")) {
            try {
                titleFreqIndex = Integer.parseInt(snapshot.get("titleFreq"));
            } catch (NumberFormatException ignored) {
            }
        }
        if (snapshot.containsKey("subtitleFreq")) {
            try {
                subtitleFreqIndex = Integer.parseInt(snapshot.get("subtitleFreq"));
            } catch (NumberFormatException ignored) {
            }
        }

        restoreHandleValues(snapshot);
        setPendingPickRestore(snapshot);
    }

    @Override
    protected boolean showsKeyFrame() {
        return false;
    }

    @Override
    protected String getHeaderTitle() {
        return UIText.of("ponderer.ui.trigger_editor");
    }

    @Override
    protected void init() {
        super.init();
        confirmButton.withCallback(this::doConfirm);
    }

    @Override
    protected void collectFormEntries(List<StepEditorEntry> entries) {
        if (pendingStructureSelection != null) {
            structureField.setValue(pendingStructureSelection);
            pendingStructureSelection = null;
        }

        entries.add(StepEditorEntries.textWithJeiAndHeldItem(
            itemField,
            "ponderer.ui.scene_desc.item_id",
            null,
            UIText.of("ponderer.ui.scene_desc.hint.item_id"),
            IdFieldMode.ITEM,
            this::applyHeldItem));
        entries.add(StepEditorEntries.nbtText(
            itemNbtField,
            "ponderer.ui.scene_desc.item_nbt",
            null,
            UIText.of("ponderer.ui.scene_desc.hint.item_nbt"),
            124,
            "nbt"));
        entries.add(StepEditorEntries.cycleButton(
            "ponderer.ui.trigger_editor.trigger_mode",
            "ponderer.ui.trigger_editor.trigger_mode.tooltip",
            100,
            () -> {
                triggerModeIndex = (triggerModeIndex + 1) % TRIGGER_MODES.length;
                rebuildFormPreservingState();
            },
            () -> UIText.of("ponderer.ui.trigger_editor.trigger_mode." + TRIGGER_MODES[triggerModeIndex])));

        if (triggerModeIndex == 0) {
            return;
        }

        entries.add(StepEditorEntries.cycleButton(
            "ponderer.ui.trigger_editor.hint_style.auto",
            "ponderer.ui.trigger_editor.hint_style.auto.tooltip",
            100,
            () -> autoFreqIndex = (autoFreqIndex + 1) % FREQ_VALUES.length,
            () -> UIText.of("ponderer.ui.trigger_editor.hint_freq." + FREQ_KEYS[autoFreqIndex]),
            () -> autoFreqIndex != 0 ? 0xFF5555 : 0xFFFFFF));

        entries.add(StepEditorEntries.cycleButton(
            "ponderer.ui.trigger_editor.hint_style.title",
            "ponderer.ui.trigger_editor.hint_style.title.tooltip",
            100,
            () -> {
                titleFreqIndex = (titleFreqIndex + 1) % FREQ_VALUES.length;
                rebuildFormPreservingState();
            },
            () -> UIText.of("ponderer.ui.trigger_editor.hint_freq." + FREQ_KEYS[titleFreqIndex])));
        if (titleFreqIndex != 0) {
            entries.add(StepEditorEntries.text(
                titleTextField,
                "ponderer.ui.trigger_editor.hint_text",
                "ponderer.ui.trigger_editor.hint_text.tooltip",
                UIText.of("ponderer.ui.trigger_editor.hint_text.placeholder"),
                120));
        }

        entries.add(StepEditorEntries.cycleButton(
            "ponderer.ui.trigger_editor.hint_style.subtitle",
            "ponderer.ui.trigger_editor.hint_style.subtitle.tooltip",
            100,
            () -> {
                subtitleFreqIndex = (subtitleFreqIndex + 1) % FREQ_VALUES.length;
                rebuildFormPreservingState();
            },
            () -> UIText.of("ponderer.ui.trigger_editor.hint_freq." + FREQ_KEYS[subtitleFreqIndex])));
        if (subtitleFreqIndex != 0) {
            entries.add(StepEditorEntries.text(
                subtitleTextField,
                "ponderer.ui.trigger_editor.hint_text",
                "ponderer.ui.trigger_editor.hint_text.tooltip",
                UIText.of("ponderer.ui.trigger_editor.hint_text.placeholder"),
                120));
        }

        String mode = TRIGGER_MODES[triggerModeIndex];
        if ("structure".equals(mode)) {
            entries.add(StepEditorEntries.textWithButton(
                structureField,
                "ponderer.ui.trigger_editor.trigger_structure",
                null,
                UIText.of("ponderer.ui.trigger_editor.hint.trigger_structure"),
                124,
                this::openStructureList,
                () -> "L",
                () -> 0xFFFFFF,
                UIText.of("ponderer.ui.scene_desc.structure_list_title")));
        } else if ("coordinate".equals(mode)) {
            entries.add(StepEditorEntries.xyzWithButton(
                coord1Field,
                "ponderer.ui.trigger_editor.trigger_coord1",
                null,
                this::startCoordinatePick,
                () -> "+",
                () -> 0x80FFFF,
                UIText.of("ponderer.ui.pick.tooltip")));
            entries.add(StepEditorEntries.xyz(
                coord2Field,
                "ponderer.ui.trigger_editor.trigger_coord2",
                null));
        }
    }

    private void applyHeldItem(ItemStack stack) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        itemField.setValue(itemId);
        if (stack.getTag() != null && !stack.getTag().isEmpty()) {
            itemNbtField.setValue(stack.getTag().toString());
        } else {
            itemNbtField.setValue("");
        }
    }

    private void openStructureList() {
        Minecraft.getInstance().setScreen(new StructureListScreen(this, selected -> pendingStructureSelection = selected));
    }

    private void startCoordinatePick() {
        Map<String, String> snapshot = snapshotForm();
        CoordPickState.startPick(snapshot, scene, sceneIndex, parent);
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    protected String getStepType() {
        return "trigger_editor";
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        return null;
    }

    @Override
    protected void appendCustomSnapshot(Map<String, String> snapshot) {
        snapshot.put("triggerMode", String.valueOf(triggerModeIndex));
        snapshot.put("autoFreq", String.valueOf(autoFreqIndex));
        snapshot.put("titleFreq", String.valueOf(titleFreqIndex));
        snapshot.put("subtitleFreq", String.valueOf(subtitleFreqIndex));
        titleTextField.snapshot(snapshot);
        subtitleTextField.snapshot(snapshot);
        structureField.snapshot(snapshot);
        coord1Field.snapshot(snapshot);
        coord2Field.snapshot(snapshot);
    }

    @Override
    protected void restoreCustomSnapshot(Map<String, String> snapshot) {
        if (snapshot.containsKey("triggerMode")) {
            try {
                triggerModeIndex = Integer.parseInt(snapshot.get("triggerMode"));
            } catch (NumberFormatException ignored) {
                triggerModeIndex = 0;
            }
        }
        if (snapshot.containsKey("autoFreq")) {
            try {
                autoFreqIndex = Integer.parseInt(snapshot.get("autoFreq"));
            } catch (NumberFormatException ignored) {
                autoFreqIndex = 0;
            }
        }
        if (snapshot.containsKey("titleFreq")) {
            try {
                titleFreqIndex = Integer.parseInt(snapshot.get("titleFreq"));
            } catch (NumberFormatException ignored) {
                titleFreqIndex = 0;
            }
        }
        if (snapshot.containsKey("subtitleFreq")) {
            try {
                subtitleFreqIndex = Integer.parseInt(snapshot.get("subtitleFreq"));
            } catch (NumberFormatException ignored) {
                subtitleFreqIndex = 0;
            }
        }

        restoreHandleValues(snapshot);
        if (snapshot.containsKey("nbt")) {
            itemNbtField.setValue(snapshot.get("nbt"));
        }
        restoreNbtPickNotice(snapshot);
    }

    private void restoreHandleValues(Map<String, String> snapshot) {
        itemField.restore(snapshot);
        itemNbtField.restore(snapshot);
        titleTextField.restore(snapshot);
        subtitleTextField.restore(snapshot);
        structureField.restore(snapshot);
        coord1Field.restore(snapshot);
        coord2Field.restore(snapshot);
    }

    @Override
    protected void renderFormForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderFormForeground(graphics, mouseX, mouseY, partialTicks);
        if (triggerModeIndex != 0 && autoFreqIndex != 0) {
            String warning = UIText.of("ponderer.ui.trigger_editor.auto_warning");
            int warnY = confirmButton.getY() - 12;
            graphics.drawCenteredString(Minecraft.getInstance().font, warning,
                guiLeft + WINDOW_W / 2, warnY, 0xFF5555);
        }
    }

    private void doConfirm() {
        if (!doSave()) return;
        returnToParent();
    }

    private boolean doSave() {
        errorMessage = null;

        String newItemId = itemField.getValue().trim();
        if (newItemId.isEmpty()) {
            errorMessage = UIText.of("ponderer.ui.scene_desc.empty_item");
            return false;
        }

        String oldItemId = scene.items != null && !scene.items.isEmpty() ? scene.items.get(0) : "";
        if (!newItemId.equals(oldItemId) && !pendingItemDuplicateConfirm) {
            for (DslScene s : SceneRuntime.getScenes()) {
                if (s != scene && s.items != null && s.items.contains(newItemId)) {
                    Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(
                        confirmed -> {
                            if (confirmed) {
                                pendingItemDuplicateConfirm = true;
                                Minecraft.getInstance().setScreen(this);
                                doConfirm();
                            } else {
                                Minecraft.getInstance().setScreen(this);
                            }
                        },
                        Component.translatable("ponderer.ui.scene_desc.error.item_exists_title"),
                        Component.translatable("ponderer.ui.scene_desc.error.item_exists", newItemId)));
                    return false;
                }
            }
        }
        pendingItemDuplicateConfirm = false;

        DslScene candidate = SceneStore.copyScene(scene);
        if (candidate == null) {
            errorMessage = UIText.of("ponderer.ui.save_error.io", "Unable to prepare scene copy");
            return false;
        }

        candidate.items = List.of(newItemId);
        String nbt = itemNbtField.getValue().trim();
        candidate.nbtFilter = nbt.isEmpty() ? null : nbt;

        String triggerMode = TRIGGER_MODES[triggerModeIndex];
        if ("structure".equals(triggerMode)) {
            String structId = structureField.getValue().trim();
            if (!structId.isEmpty() && !isValidStructureId(structId)) {
                errorMessage = UIText.of("ponderer.ui.trigger_editor.error.invalid_structure");
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
            candidate.triggerCoord1 = List.of(
                parseIntOr(coord1Field.x(), 0),
                parseIntOr(coord1Field.y(), 0),
                parseIntOr(coord1Field.z(), 0));
            candidate.triggerCoord2 = List.of(
                parseIntOr(coord2Field.x(), 0),
                parseIntOr(coord2Field.y(), 0),
                parseIntOr(coord2Field.z(), 0));
            candidate.triggerStructure = null;
            candidate.triggerStructureRange = null;
        } else {
            candidate.triggerStructure = null;
            candidate.triggerStructureRange = null;
            candidate.triggerCoord1 = null;
            candidate.triggerCoord2 = null;
        }

        SceneStore.LocalSaveResult saveResult = SceneStore.saveSceneToLocalDetailed(candidate);
        if (!saveResult.isSuccess()) {
            errorMessage = UIText.saveError(saveResult);
            return false;
        }

        scene.items = candidate.items;
        scene.nbtFilter = candidate.nbtFilter;
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
        Minecraft.getInstance().execute(PonderIndex::reload);
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
