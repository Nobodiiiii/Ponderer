package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import com.nododiiiii.ponderer.ponder.SceneStore;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nullable;
import java.util.HashMap;
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

    private int triggerModeIndex = 0;

    // Per-hint-style frequency index (0=off, 1=always, 2=first_time, 3=until_read)
    private int autoFreqIndex = 0;
    private int titleFreqIndex = 0;
    private int subtitleFreqIndex = 0;

    // Custom hint text fields (for title/subtitle)
    private HintableTextFieldWidget titleTextField;
    private HintableTextFieldWidget subtitleTextField;

    // Carrier item + NBT (always visible at the top)
    private FieldWithJeiAndHeldItem itemRow;
    private HintableTextFieldWidget itemNbtField;
    private boolean pendingItemDuplicateConfirm = false;

    // Structure trigger
    private HintableTextFieldWidget structureField;
    private PonderButton structureBrowseBtn;
    @Nullable private String pendingStructureSelection = null;

    // Coordinate trigger
    private XyzFieldGroup coord1Group;
    private XyzFieldGroup coord2Group;
    private PonderButton pickBtn1;

    public TriggerEditorScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.trigger_editor"), scene, sceneIndex, parent);

        if ("structure".equals(scene.triggerMode)) triggerModeIndex = 1;
        else if ("coordinate".equals(scene.triggerMode)) triggerModeIndex = 2;
        else triggerModeIndex = 0;

        // Load per-style frequencies from new fields
        autoFreqIndex = freqToIndex(scene.hintAuto);
        titleFreqIndex = freqToIndex(scene.hintTitle);
        subtitleFreqIndex = freqToIndex(scene.hintSubtitle);

        // Legacy migration: if new fields are all off but old hintStyle field exists
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
    }

    private static int freqToIndex(@Nullable String freq) {
        if ("always".equals(freq)) return 1;
        if ("first_time".equals(freq)) return 2;
        if ("until_read".equals(freq)) return 3;
        return 0; // off
    }

    private static String resolveLegacyFrequency(DslScene scene) {
        if ("first_time".equals(scene.hintFrequency)) return "first_time";
        if ("until_read".equals(scene.hintFrequency)) return "until_read";
        if (Boolean.TRUE.equals(scene.onlyFirstTime)) return "first_time";
        return "always";
    }

    public void setPendingFormRestore(Map<String, String> snapshot) {
        // Pre-apply triggerModeIndex and frequency indices from snapshot BEFORE init/buildForm,
        // so that buildForm() creates the correct form layout (e.g. "coordinate" mode fields).
        if (snapshot.containsKey("triggerMode")) {
            try { triggerModeIndex = Integer.parseInt(snapshot.get("triggerMode")); } catch (NumberFormatException ignored) {}
        }
        if (snapshot.containsKey("autoFreq")) {
            try { autoFreqIndex = Integer.parseInt(snapshot.get("autoFreq")); } catch (NumberFormatException ignored) {}
        }
        if (snapshot.containsKey("titleFreq")) {
            try { titleFreqIndex = Integer.parseInt(snapshot.get("titleFreq")); } catch (NumberFormatException ignored) {}
        }
        if (snapshot.containsKey("subtitleFreq")) {
            try { subtitleFreqIndex = Integer.parseInt(snapshot.get("subtitleFreq")); } catch (NumberFormatException ignored) {}
        }
        setPendingPickRestore(snapshot);
    }

    @Override
    protected boolean showsKeyFrame() { return false; }

    @Override
    protected String getHeaderTitle() {
        return UIText.of("ponderer.ui.trigger_editor");
    }

    @Override
    protected int getFormRowCount() {
        int rows = 3; // item ID + item NBT + trigger mode
        if (triggerModeIndex == 0) return rows;
        rows += 3; // auto + title + subtitle
        if (titleFreqIndex != 0) rows++; // title custom text
        if (subtitleFreqIndex != 0) rows++; // subtitle custom text
        String mode = TRIGGER_MODES[triggerModeIndex];
        if ("structure".equals(mode)) rows += 1; // structure
        else if ("coordinate".equals(mode)) rows += 2; // coord1 + coord2
        return rows;
    }

    @Override
    protected void init() {
        super.init();
        confirmButton.withCallback(this::doConfirm);
    }

    @Override
    protected void buildForm() {
        // Clear mode-specific field references (they get recreated if needed)
        structureField = null;
        structureBrowseBtn = null;
        coord1Group = null;
        coord2Group = null;
        pickBtn1 = null;
        titleTextField = null;
        subtitleTextField = null;
        itemRow = null;
        itemNbtField = null;

        beginForm();

        // ---- Carrier item ID + NBT (always shown) ----
        itemRow = addFormTextFieldWithJeiAndHeldItem(
                "ponderer.ui.scene_desc.item_id", null,
                UIText.of("ponderer.ui.scene_desc.hint.item_id"),
                IdFieldMode.ITEM,
                stack -> {
                    String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    itemRow.field().setValue(itemId);
                    var custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                    if (itemNbtField != null && !custom.isEmpty()) {
                        itemNbtField.setValue(custom.toString());
                    } else if (itemNbtField != null) {
                        itemNbtField.setValue("");
                    }
                });
        String currentItem = (scene.items != null && !scene.items.isEmpty()) ? scene.items.get(0) : "";
        itemRow.field().setValue(currentItem);

        itemNbtField = addFormNbtField(
                "ponderer.ui.scene_desc.item_nbt", null,
                UIText.of("ponderer.ui.scene_desc.hint.item_nbt"),
                124, "nbt");
        itemNbtField.setValue(scene.nbtFilter != null ? scene.nbtFilter : "");

        // ---- Trigger mode cycle button ----
        addFormCycleButton(
                "ponderer.ui.trigger_editor.trigger_mode",
                "ponderer.ui.trigger_editor.trigger_mode.tooltip",
                100,
                () -> {
                    Map<String, String> snap = snapshotForm();
                    triggerModeIndex = (triggerModeIndex + 1) % TRIGGER_MODES.length;
                    setPendingPickRestore(snap);
                    init(Minecraft.getInstance(), this.width, this.height);
                },
                () -> UIText.of("ponderer.ui.trigger_editor.trigger_mode." + TRIGGER_MODES[triggerModeIndex]));

        // If "none" mode, no more rows
        if (triggerModeIndex == 0) return;

        // ---- 3 parallel hint style rows, each with its own frequency cycle ----
        // Auto trigger: red text when enabled
        addFormCycleButton(
                "ponderer.ui.trigger_editor.hint_style.auto",
                "ponderer.ui.trigger_editor.hint_style.auto.tooltip",
                100,
                () -> {
                    autoFreqIndex = (autoFreqIndex + 1) % FREQ_VALUES.length;
                    // Rebuild to show/hide warning
                    Map<String, String> snap = snapshotForm();
                    setPendingPickRestore(snap);
                    init(Minecraft.getInstance(), this.width, this.height);
                },
                () -> UIText.of("ponderer.ui.trigger_editor.hint_freq." + FREQ_KEYS[autoFreqIndex]),
                () -> autoFreqIndex != 0 ? 0xFF5555 : 0xFFFFFF);

        // Title hint
        addFormCycleButton(
                "ponderer.ui.trigger_editor.hint_style.title",
                "ponderer.ui.trigger_editor.hint_style.title.tooltip",
                100,
                () -> {
                    titleFreqIndex = (titleFreqIndex + 1) % FREQ_VALUES.length;
                    Map<String, String> snap = snapshotForm();
                    setPendingPickRestore(snap);
                    init(Minecraft.getInstance(), this.width, this.height);
                },
                () -> UIText.of("ponderer.ui.trigger_editor.hint_freq." + FREQ_KEYS[titleFreqIndex]));

        // Title custom text (only when enabled)
        if (titleFreqIndex != 0) {
            titleTextField = addFormTextField(
                    "ponderer.ui.trigger_editor.hint_text",
                    "ponderer.ui.trigger_editor.hint_text.tooltip",
                    UIText.of("ponderer.ui.trigger_editor.hint_text.placeholder"), 120);
            titleTextField.setValue(scene.hintTitleText != null ? scene.hintTitleText : "");
        }

        // Subtitle hint
        addFormCycleButton(
                "ponderer.ui.trigger_editor.hint_style.subtitle",
                "ponderer.ui.trigger_editor.hint_style.subtitle.tooltip",
                100,
                () -> {
                    subtitleFreqIndex = (subtitleFreqIndex + 1) % FREQ_VALUES.length;
                    Map<String, String> snap = snapshotForm();
                    setPendingPickRestore(snap);
                    init(Minecraft.getInstance(), this.width, this.height);
                },
                () -> UIText.of("ponderer.ui.trigger_editor.hint_freq." + FREQ_KEYS[subtitleFreqIndex]));

        // Subtitle custom text (only when enabled)
        if (subtitleFreqIndex != 0) {
            subtitleTextField = addFormTextField(
                    "ponderer.ui.trigger_editor.hint_text",
                    "ponderer.ui.trigger_editor.hint_text.tooltip",
                    UIText.of("ponderer.ui.trigger_editor.hint_text.placeholder"), 120);
            subtitleTextField.setValue(scene.hintSubtitleText != null ? scene.hintSubtitleText : "");
        }

        // ---- Mode-specific fields ----
        String mode = TRIGGER_MODES[triggerModeIndex];
        if ("structure".equals(mode)) {
            // Structure field + browse button
            addFormLabel("ponderer.ui.trigger_editor.trigger_structure", null);
            structureField = createTextField(fieldX(), formY(), 124, 18,
                    UIText.of("ponderer.ui.trigger_editor.hint.trigger_structure"));
            String structVal = pendingStructureSelection != null ? pendingStructureSelection
                    : (scene.triggerStructure != null ? scene.triggerStructure : "");
            structureField.setValue(structVal);
            pendingStructureSelection = null;

            // Browse button: text "L", positioned right of the field
            structureBrowseBtn = new PonderButton(fieldX() + 124 + 5, formY() + 3, 14, 12);
            structureBrowseBtn.withCallback(() -> {
                Minecraft.getInstance().setScreen(new StructureListScreen(this, selected -> {
                    pendingStructureSelection = selected;
                }));
            });
            addRenderableWidget(structureBrowseBtn);
            nextFormRow();
        } else if ("coordinate".equals(mode)) {
            coord1Group = addFormXyzRow("ponderer.ui.trigger_editor.trigger_coord1", null);
            if (scene.triggerCoord1 != null && scene.triggerCoord1.size() >= 3) {
                coord1Group.x().setValue(String.valueOf(scene.triggerCoord1.get(0)));
                coord1Group.y().setValue(String.valueOf(scene.triggerCoord1.get(1)));
                coord1Group.z().setValue(String.valueOf(scene.triggerCoord1.get(2)));
            } else {
                coord1Group.x().setValue("0");
                coord1Group.y().setValue("0");
                coord1Group.z().setValue("0");
            }
            // Single pick button on coord1 row: picks both points sequentially
            pickBtn1 = new PonderButton(fieldX() + 3 * (38 + 5), formY() - ROW_HEIGHT + 3, 14, 12);
            pickBtn1.withCallback(() -> {
                Map<String, String> snap = snapshotForm();
                CoordPickState.startPick(snap, scene, sceneIndex, parent);
                Minecraft.getInstance().setScreen(null);
            });
            addRenderableWidget(pickBtn1);

            coord2Group = addFormXyzRow("ponderer.ui.trigger_editor.trigger_coord2", null);
            if (scene.triggerCoord2 != null && scene.triggerCoord2.size() >= 3) {
                coord2Group.x().setValue(String.valueOf(scene.triggerCoord2.get(0)));
                coord2Group.y().setValue(String.valueOf(scene.triggerCoord2.get(1)));
                coord2Group.z().setValue(String.valueOf(scene.triggerCoord2.get(2)));
            } else {
                coord2Group.x().setValue("0");
                coord2Group.y().setValue("0");
                coord2Group.z().setValue("0");
            }
        }

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
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        if (snapshot.containsKey("triggerMode")) {
            try { triggerModeIndex = Integer.parseInt(snapshot.get("triggerMode")); } catch (NumberFormatException ignored) {}
        }
        if (snapshot.containsKey("autoFreq")) {
            try { autoFreqIndex = Integer.parseInt(snapshot.get("autoFreq")); } catch (NumberFormatException ignored) {}
        }
        if (snapshot.containsKey("titleFreq")) {
            try { titleFreqIndex = Integer.parseInt(snapshot.get("titleFreq")); } catch (NumberFormatException ignored) {}
        }
        if (snapshot.containsKey("subtitleFreq")) {
            try { subtitleFreqIndex = Integer.parseInt(snapshot.get("subtitleFreq")); } catch (NumberFormatException ignored) {}
        }
        if (snapshot.containsKey("itemId") && itemRow != null)
            itemRow.field().setValue(snapshot.get("itemId"));
        if (snapshot.containsKey("itemNbt") && itemNbtField != null)
            itemNbtField.setValue(snapshot.get("itemNbt"));
        if (titleTextField != null && snapshot.containsKey("titleText"))
            titleTextField.setValue(snapshot.get("titleText"));
        if (subtitleTextField != null && snapshot.containsKey("subtitleText"))
            subtitleTextField.setValue(snapshot.get("subtitleText"));
        if (structureField != null && snapshot.containsKey("structure"))
            structureField.setValue(snapshot.get("structure"));

        if (coord1Group != null) {
            if (snapshot.containsKey("coord1_x")) coord1Group.x().setValue(snapshot.get("coord1_x"));
            if (snapshot.containsKey("coord1_y")) coord1Group.y().setValue(snapshot.get("coord1_y"));
            if (snapshot.containsKey("coord1_z")) coord1Group.z().setValue(snapshot.get("coord1_z"));
        }
        if (coord2Group != null) {
            if (snapshot.containsKey("coord2_x")) coord2Group.x().setValue(snapshot.get("coord2_x"));
            if (snapshot.containsKey("coord2_y")) coord2Group.y().setValue(snapshot.get("coord2_y"));
            if (snapshot.containsKey("coord2_z")) coord2Group.z().setValue(snapshot.get("coord2_z"));
        }
    }

    @Override
    protected void renderFormForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderFormForeground(graphics, mouseX, mouseY, partialTicks);
        var font = Minecraft.getInstance().font;
        if (structureBrowseBtn != null && structureBrowseBtn.visible) {
            graphics.drawCenteredString(font, "L",
                    structureBrowseBtn.getX() + 7, structureBrowseBtn.getY() + 2, 0xFFFFFF);
        }
        // Reuse the existing pick button rendering style ("+")
        if (pickBtn1 != null && pickBtn1.visible) {
            renderPickButtonLabel(graphics, pickBtn1);
        }
        // Red warning above confirm/cancel when auto-trigger is enabled
        if (triggerModeIndex != 0 && autoFreqIndex != 0) {
            String warning = UIText.of("ponderer.ui.trigger_editor.auto_warning");
            int warnY = confirmButton.getY() - 12;
            graphics.drawCenteredString(font, warning, guiLeft + WINDOW_W / 2, warnY, 0xFF5555);
        }
    }

    @Override
    protected Map<String, String> snapshotForm() {
        Map<String, String> m = new HashMap<>();
        m.put("triggerMode", String.valueOf(triggerModeIndex));
        m.put("autoFreq", String.valueOf(autoFreqIndex));
        m.put("titleFreq", String.valueOf(titleFreqIndex));
        m.put("subtitleFreq", String.valueOf(subtitleFreqIndex));
        if (itemRow != null) m.put("itemId", itemRow.field().getValue());
        if (itemNbtField != null) m.put("itemNbt", itemNbtField.getValue());

        if (titleTextField != null) m.put("titleText", titleTextField.getValue());
        if (subtitleTextField != null) m.put("subtitleText", subtitleTextField.getValue());

        if (structureField != null) m.put("structure", structureField.getValue());

        if (coord1Group != null) {
            m.put("coord1_x", coord1Group.x().getValue());
            m.put("coord1_y", coord1Group.y().getValue());
            m.put("coord1_z", coord1Group.z().getValue());
        }
        if (coord2Group != null) {
            m.put("coord2_x", coord2Group.x().getValue());
            m.put("coord2_y", coord2Group.y().getValue());
            m.put("coord2_z", coord2Group.z().getValue());
        }
        return m;
    }

    private void doConfirm() {
        if (!doSave()) return;
        returnToParent();
    }

    /** Validate and save form data. Returns false if validation failed. */
    private boolean doSave() {
        errorMessage = null;

        String newItemId = itemRow != null ? itemRow.field().getValue().trim() : "";
        if (newItemId.isEmpty()) {
            errorMessage = UIText.of("ponderer.ui.scene_desc.empty_item");
            return false;
        }

        String oldItemId = (scene.items != null && !scene.items.isEmpty()) ? scene.items.get(0) : "";
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

        scene.items = List.of(newItemId);
        String nbt = itemNbtField != null ? itemNbtField.getValue().trim() : "";
        scene.nbtFilter = nbt.isEmpty() ? null : nbt;

        String triggerMode = TRIGGER_MODES[triggerModeIndex];

        // Validate structure
        if ("structure".equals(triggerMode) && structureField != null) {
            String structId = structureField.getValue().trim();
            if (!structId.isEmpty() && !isValidStructureId(structId)) {
                errorMessage = UIText.of("ponderer.ui.trigger_editor.error.invalid_structure");
                return false;
            }
        }

        // Save trigger settings
        scene.triggerMode = "none".equals(triggerMode) ? null : triggerMode;

        // Save per-style frequencies
        scene.hintAuto = FREQ_VALUES[autoFreqIndex];
        scene.hintTitle = FREQ_VALUES[titleFreqIndex];
        scene.hintSubtitle = FREQ_VALUES[subtitleFreqIndex];

        // Save custom hint text
        scene.hintTitleText = titleTextField != null ? emptyToNull(titleTextField.getValue()) : null;
        scene.hintSubtitleText = subtitleTextField != null ? emptyToNull(subtitleTextField.getValue()) : null;

        // Clear legacy fields
        scene.hintStyle = null;
        scene.hintFrequency = null;
        scene.onlyFirstTime = null;

        if ("structure".equals(triggerMode)) {
            scene.triggerStructure = structureField != null ? structureField.getValue().trim() : null;
            scene.triggerStructureRange = null;
            scene.triggerCoord1 = null;
            scene.triggerCoord2 = null;
        } else if ("coordinate".equals(triggerMode)) {
            scene.triggerCoord1 = List.of(
                    parseIntOr(coord1Group != null ? coord1Group.x() : null, 0),
                    parseIntOr(coord1Group != null ? coord1Group.y() : null, 0),
                    parseIntOr(coord1Group != null ? coord1Group.z() : null, 0));
            scene.triggerCoord2 = List.of(
                    parseIntOr(coord2Group != null ? coord2Group.x() : null, 0),
                    parseIntOr(coord2Group != null ? coord2Group.y() : null, 0),
                    parseIntOr(coord2Group != null ? coord2Group.z() : null, 0));
            scene.triggerStructure = null;
            scene.triggerStructureRange = null;
        } else {
            scene.triggerStructure = null;
            scene.triggerStructureRange = null;
            scene.triggerCoord1 = null;
            scene.triggerCoord2 = null;
        }

        SceneStore.saveSceneToLocal(scene);
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
        } catch (Exception ignored) {}
        return true;
    }

    private static int parseIntOr(@Nullable HintableTextFieldWidget field, int fallback) {
        if (field == null) return fallback;
        try {
            return Integer.parseInt(field.getValue().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Nullable
    private static String emptyToNull(@Nullable String s) {
        return s != null && !s.trim().isEmpty() ? s.trim() : null;
    }
}
