package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeListScreen;
import com.nododiiiii.ponderer.ui.catnip.BlockPropertyListEntry;
import com.nododiiiii.ponderer.ui.catnip.ButtonListEntry;
import com.nododiiiii.ponderer.ui.catnip.DualTextListEntry;
import com.nododiiiii.ponderer.ui.catnip.LocalizedTextListEntry;
import com.nododiiiii.ponderer.ui.catnip.PlainTextListEntry;
import com.nododiiiii.ponderer.ui.catnip.ToggleListEntry;
import com.nododiiiii.ponderer.ui.catnip.XyzListEntry;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public abstract class AbstractStepEditorScreen extends AbstractDeclarativeListScreen implements JeiAwareScreen {

    protected static final int STEP_EDITOR_LIST_WIDTH = 300;

    protected final DslScene scene;
    protected final int sceneIndex;
    protected final SceneEditorScreen parent;
    @Nullable
    protected Screen returnScreen;

    protected final int editIndex;
    @Nullable
    protected final DslScene.DslStep existingStep;

    @Nullable
    protected BoxWidget confirmButton;
    @Nullable
    protected BoxWidget cancelButton;
    protected String errorMessage = null;
    protected String infoMessage = null;
    protected boolean attachKeyFrame = false;
    protected int insertAfterIndex = -1;

    @Nullable
    protected List<String[]> blockPropEntries;
    @Nullable
    protected List<HintableTextFieldWidget[]> blockPropFields;

    private boolean initialPopulateDone = false;
    @Nullable
    private Map<String, String> pendingPickRestore = null;
    @Nullable
    private Map<String, String> baselineSnapshot = null;

    private boolean jeiActive = false;
    @Nullable
    private HintableTextFieldWidget jeiTargetField = null;
    @Nullable
    private IdFieldMode jeiMode = null;

    private final List<StepEditorEntry> formEntries = new ArrayList<>();
    @Nullable
    private List<ConfigScreenList.Entry> currentEntries = null;

    protected static final Map<String, Integer> PALETTE_COLORS = Map.ofEntries(
        Map.entry("white", 0xEEEEEE),
        Map.entry("black", 0x221111),
        Map.entry("red", 0xFF5D6C),
        Map.entry("green", 0x8CBA51),
        Map.entry("blue", 0x5F6CAF),
        Map.entry("slow", 0x22FF22),
        Map.entry("medium", 0x0084FF),
        Map.entry("fast", 0xFF55FF),
        Map.entry("input", 0x7FCDE0),
        Map.entry("output", 0xDDC166));

    protected record FieldWithJei(HintableTextFieldWidget field, @Nullable BoxWidget jeiBtn) {
    }

    protected record XyzFieldGroup(HintableTextFieldWidget x, HintableTextFieldWidget y,
                                   HintableTextFieldWidget z, @Nullable BoxWidget pickBtn) {
    }

    protected record FieldWithLang(HintableTextFieldWidget field, BoxWidget langBtn) {
    }

    protected record FieldWithButton(HintableTextFieldWidget field, BoxWidget button) {
    }

    protected record DualFieldGroup(HintableTextFieldWidget first, HintableTextFieldWidget second) {
    }

    protected record FieldWithJeiAndBlockPick(HintableTextFieldWidget field, @Nullable BoxWidget jeiBtn,
                                              BoxWidget blockPickBtn) {
    }

    protected record FieldWithJeiAndHeldItem(HintableTextFieldWidget field, @Nullable BoxWidget jeiBtn,
                                             BoxWidget heldItemBtn) {
    }

    protected record FieldWithJeiAndNbtPick(HintableTextFieldWidget field, @Nullable BoxWidget jeiBtn,
                                            BoxWidget nbtPickBtn) {
    }

    protected static int getPaletteColor(String name) {
        return PALETTE_COLORS.getOrDefault(name.toLowerCase(), 0xFFFFFF);
    }

    protected AbstractStepEditorScreen(net.minecraft.network.chat.Component title, DslScene scene, int sceneIndex,
                                       SceneEditorScreen parent) {
        this(title, scene, sceneIndex, parent, -1, null);
    }

    protected AbstractStepEditorScreen(net.minecraft.network.chat.Component title, DslScene scene, int sceneIndex,
                                       SceneEditorScreen parent, int editIndex,
                                       @Nullable DslScene.DslStep existingStep) {
        super(parent, "ponderer.ui.scope.editor", "ponderer.ui.step_editor", STEP_EDITOR_LIST_WIDTH);
        this.scene = scene;
        this.sceneIndex = sceneIndex;
        this.parent = parent;
        this.editIndex = editIndex;
        this.existingStep = existingStep;
    }

    protected boolean isEditMode() {
        return editIndex >= 0 && existingStep != null;
    }

    public AbstractStepEditorScreen setReturnScreen(@Nullable Screen returnScreen) {
        this.returnScreen = returnScreen;
        return this;
    }

    public AbstractStepEditorScreen setInsertAfterIndex(int index) {
        this.insertAfterIndex = index;
        return this;
    }

    public AbstractStepEditorScreen setPendingPickRestore(@Nullable Map<String, String> snapshot) {
        this.pendingPickRestore = snapshot;
        if (snapshot != null) {
            prepareSnapshotForBuild(snapshot);
        }
        return this;
    }

    @Override
    protected void init() {
        if (usesBlockProps()) {
            preExtractBlockProps();
        }
        if (!initialPopulateDone && isEditMode()) {
            populateFromStep(existingStep);
        }
        if (pendingPickRestore != null) {
            prepareSnapshotForBuild(pendingPickRestore);
        }

        super.init();

        confirmButton = saveChanges;
        cancelButton = goBack;
        if (saveChanges != null) {
            saveChanges.withCallback(this::saveAndClose);
        }

        if (!initialPopulateDone) {
            if (pendingPickRestore != null) {
                restoreFromSnapshot(pendingPickRestore);
                pendingPickRestore = null;
            }
            baselineSnapshot = snapshotForm();
            initialPopulateDone = true;
        }

        syncStatusMessages();
    }

    @Override
    protected final void collectEntries(List<ConfigScreenList.Entry> entries) {
        currentEntries = entries;
        formEntries.clear();
        collectFormEntries(formEntries);
        for (StepEditorEntry entry : formEntries) {
            entry.build(this);
        }
        if (showsKeyFrame()) {
            addFormToggle(
                "ponderer.ui.key_frame",
                "ponderer.ui.key_frame.tooltip",
                () -> attachKeyFrame,
                () -> attachKeyFrame = !attachKeyFrame);
        }
        currentEntries = null;
    }

    @Override
    protected String getBreadcrumbTitleText() {
        return getHeaderTitle() + (isEditMode() ? UIText.of("ponderer.ui.edit_suffix") : "");
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        syncStatusMessages();
        super.renderWindow(graphics, mouseX, mouseY, partialTicks);
        renderFormForeground(graphics, mouseX, mouseY, partialTicks);
    }

    protected void renderFormForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
    }

    @Override
    protected boolean hasUnsavedChanges() {
        return getUnsavedChangeCount() > 0;
    }

    @Override
    protected int getUnsavedChangeCount() {
        if (baselineSnapshot == null) {
            return 0;
        }
        Map<String, String> current = snapshotForm();
        Set<String> keys = new HashSet<>(baselineSnapshot.keySet());
        keys.addAll(current.keySet());

        int dirtyFields = 0;
        for (String key : keys) {
            if (!java.util.Objects.equals(baselineSnapshot.get(key), current.get(key))) {
                dirtyFields++;
            }
        }
        return dirtyFields;
    }

    @Override
    protected boolean saveEdits() {
        errorMessage = null;
        infoMessage = null;

        DslScene.DslStep step = buildStep();
        if (step == null) {
            syncStatusMessages();
            return false;
        }

        if (attachKeyFrame) {
            step.attachKeyFrame = true;
        }

        if (isEditMode()) {
            parent.replaceStepAndSave(editIndex, step);
        } else {
            parent.insertStepAndSave(insertAfterIndex, step);
        }

        baselineSnapshot = snapshotForm();
        syncStatusMessages();
        return true;
    }

    @Override
    protected void discardEdits() {
        errorMessage = null;
        infoMessage = null;
        if (baselineSnapshot == null) {
            syncStatusMessages();
            return;
        }

        prepareSnapshotForBuild(baselineSnapshot);
        rebuildEntries(currentListScroll());
        restoreFromSnapshot(baselineSnapshot);
        syncStatusMessages();
    }

    @Override
    protected void attemptBackToParent() {
        if (!hasUnsavedChanges()) {
            returnToParent();
            return;
        }

        showLeavingPrompt(response -> {
            if (response == net.createmod.catnip.gui.ConfirmationScreen.Response.Cancel) {
                return;
            }
            if (response == net.createmod.catnip.gui.ConfirmationScreen.Response.Confirm) {
                if (!saveEdits()) {
                    return;
                }
            } else {
                discardEdits();
            }
            returnToParent();
        });
    }

    protected void returnToParent() {
        ScreenOpener.open(returnScreen != null ? returnScreen : parent);
    }

    @Override
    public void onClose() {
        attemptBackToParent();
    }

    protected boolean showsKeyFrame() {
        return true;
    }

    protected abstract void collectFormEntries(List<StepEditorEntry> entries);

    protected void populateFromStep(DslScene.DslStep step) {
        attachKeyFrame = Boolean.TRUE.equals(step.attachKeyFrame);
    }

    @Nullable
    protected abstract DslScene.DslStep buildStep();

    protected abstract String getHeaderTitle();

    protected abstract String getStepType();

    protected final Map<String, String> snapshotForm() {
        Map<String, String> snapshot = new HashMap<>();
        for (StepEditorEntry entry : formEntries) {
            entry.snapshot(snapshot);
        }
        snapshot.put("_keyFrame", String.valueOf(attachKeyFrame));
        appendCustomSnapshot(snapshot);
        return snapshot;
    }

    protected final void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        for (StepEditorEntry entry : formEntries) {
            entry.restore(snapshot);
        }
        restoreCustomSnapshot(snapshot);
        syncStatusMessages();
    }

    protected void appendCustomSnapshot(Map<String, String> snapshot) {
    }

    protected void restoreCustomSnapshot(Map<String, String> snapshot) {
    }

    protected final void rebuildFormPreservingState() {
        Map<String, String> snapshot = snapshotForm();
        prepareSnapshotForBuild(snapshot);
        rebuildEntries(currentListScroll());
        restoreFromSnapshot(snapshot);
    }

    protected void restoreKeyFrame(Map<String, String> snapshot) {
        if (snapshot.containsKey("_keyFrame")) {
            attachKeyFrame = Boolean.parseBoolean(snapshot.get("_keyFrame"));
        }
    }

    protected void restoreNbtPickNotice(Map<String, String> snapshot) {
        if (!snapshot.containsKey(NbtPickState.SNAPSHOT_NOTICE_KEY)) {
            return;
        }
        String pickedName = snapshot.get(NbtPickState.SNAPSHOT_NOTICE_KEY);
        String translated = UIText.of("ponderer.ui.nbt_pick.filled", pickedName);
        infoMessage = "ponderer.ui.nbt_pick.filled".equals(translated)
            ? ("NBT <- " + pickedName)
            : translated;
    }

    private boolean saveAndClose() {
        if (!saveEdits()) {
            return false;
        }
        returnToParent();
        return true;
    }

    private void syncStatusMessages() {
        if (errorMessage != null && !errorMessage.isBlank()) {
            setErrorMessage(errorMessage);
            return;
        }
        if (infoMessage != null && !infoMessage.isBlank()) {
            setInfoMessage(infoMessage);
            return;
        }
        clearStatusMessages();
    }

    private void prepareSnapshotForBuild(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        if (usesBlockProps()) {
            applyBlockPropsSnapshot(snapshot);
        }
        restoreCustomSnapshot(snapshot);
    }

    protected HintableTextFieldWidget addFormTextField(String labelKey, @Nullable String tooltipKey,
                                                       String hint, int fieldW) {
        PlainTextListEntry entry = new PlainTextListEntry(labelKey, tooltipKey, null, "", value -> {
        });
        entry.field().setHint(hint);
        entry.setPreferredFieldWidth(fieldW);
        appendEntry(entry);
        return entry.field();
    }

    protected FieldWithJei addFormTextFieldWithJei(String labelKey, @Nullable String tooltipKey,
                                                   String hint, int fieldW, IdFieldMode mode) {
        PlainTextListEntry entry = new PlainTextListEntry(labelKey, tooltipKey, null, "", value -> {
        });
        entry.field().setHint(hint);
        entry.setPreferredFieldWidth(fieldW);
        BoxWidget jeiBtn = entry.addTrailingButton(
            20,
            () -> toggleJei(entry.field(), mode),
            () -> "J",
            () -> jeiActive && jeiTargetField == entry.field() ? 0x55FF55 : 0xAAAAFF,
            UIText.of("ponderer.ui.jei_browse.tooltip"));
        appendEntry(entry);
        return new FieldWithJei(entry.field(), jeiBtn);
    }

    protected FieldWithJei addFormTextFieldWithJei(String labelKey, @Nullable String tooltipKey,
                                                   String hint, IdFieldMode mode) {
        return addFormTextFieldWithJei(labelKey, tooltipKey, hint, 124, mode);
    }

    protected FieldWithJeiAndBlockPick addFormTextFieldWithJeiAndBlockPick(String labelKey,
                                                                           @Nullable String tooltipKey,
                                                                           String hint, IdFieldMode mode,
                                                                           String nbtSnapshotKey) {
        PlainTextListEntry entry = new PlainTextListEntry(labelKey, tooltipKey, null, "", value -> {
        });
        entry.field().setHint(hint);
        entry.setPreferredFieldWidth(124);
        BoxWidget jeiBtn = entry.addTrailingButton(
            20,
            () -> toggleJei(entry.field(), mode),
            () -> "J",
            () -> jeiActive && jeiTargetField == entry.field() ? 0x55FF55 : 0xAAAAFF,
            UIText.of("ponderer.ui.jei_browse.tooltip"));
        BoxWidget blockPickBtn = entry.addTrailingButton(
            20,
            () -> startNbtPick(nbtSnapshotKey, true),
            () -> "+",
            () -> 0x66FF66,
            UIText.of("ponderer.ui.block_pick.tooltip"));
        appendEntry(entry);
        return new FieldWithJeiAndBlockPick(entry.field(), jeiBtn, blockPickBtn);
    }

    protected FieldWithJeiAndHeldItem addFormTextFieldWithJeiAndHeldItem(String labelKey,
                                                                         @Nullable String tooltipKey,
                                                                         String hint, IdFieldMode mode,
                                                                         Consumer<ItemStack> onItemPicked) {
        PlainTextListEntry entry = new PlainTextListEntry(labelKey, tooltipKey, null, "", value -> {
        });
        entry.field().setHint(hint);
        entry.setPreferredFieldWidth(124);
        BoxWidget jeiBtn = entry.addTrailingButton(
            20,
            () -> toggleJei(entry.field(), mode),
            () -> "J",
            () -> jeiActive && jeiTargetField == entry.field() ? 0x55FF55 : 0xAAAAFF,
            UIText.of("ponderer.ui.jei_browse.tooltip"));
        BoxWidget heldItemBtn = entry.addTrailingButton(
            20,
            () -> useHeldItem(onItemPicked),
            () -> "+",
            () -> 0x66FF66,
            UIText.of("ponderer.ui.held_item.tooltip"));
        appendEntry(entry);
        return new FieldWithJeiAndHeldItem(entry.field(), jeiBtn, heldItemBtn);
    }

    protected FieldWithJeiAndNbtPick addFormTextFieldWithJeiAndNbtPick(String labelKey,
                                                                       @Nullable String tooltipKey,
                                                                       String hint, IdFieldMode mode,
                                                                       String nbtSnapshotKey) {
        PlainTextListEntry entry = new PlainTextListEntry(labelKey, tooltipKey, null, "", value -> {
        });
        entry.field().setHint(hint);
        entry.setPreferredFieldWidth(124);
        BoxWidget jeiBtn = entry.addTrailingButton(
            20,
            () -> toggleJei(entry.field(), mode),
            () -> "J",
            () -> jeiActive && jeiTargetField == entry.field() ? 0x55FF55 : 0xAAAAFF,
            UIText.of("ponderer.ui.jei_browse.tooltip"));
        BoxWidget nbtPickBtn = entry.addTrailingButton(
            20,
            () -> startNbtPick(nbtSnapshotKey, false),
            () -> "+",
            () -> 0x66FF66,
            UIText.of("ponderer.ui.nbt_pick.tooltip"));
        appendEntry(entry);
        return new FieldWithJeiAndNbtPick(entry.field(), jeiBtn, nbtPickBtn);
    }

    protected HintableTextFieldWidget addFormNbtField(String labelKey, @Nullable String tooltipKey,
                                                      String hint, int fieldW, String nbtSnapshotKey) {
        PlainTextListEntry entry = new PlainTextListEntry(labelKey, tooltipKey, null, "", value -> {
        });
        entry.field().setHint(hint);
        entry.setPreferredFieldWidth(fieldW);
        entry.addTrailingButton(
            20,
            () -> startNbtPick(nbtSnapshotKey, false),
            () -> "+",
            () -> 0x66FF66,
            UIText.of("ponderer.ui.nbt_pick.tooltip"));
        appendEntry(entry);
        return entry.field();
    }

    protected FieldWithButton addFormTextFieldWithButton(String labelKey, @Nullable String tooltipKey,
                                                         String hint, int fieldW, Runnable onClick,
                                                         Supplier<String> buttonLabelGetter,
                                                         IntSupplier buttonColorGetter,
                                                         @Nullable String buttonTooltip) {
        PlainTextListEntry entry = new PlainTextListEntry(labelKey, tooltipKey, null, "", value -> {
        });
        entry.field().setHint(hint);
        entry.setPreferredFieldWidth(fieldW);
        BoxWidget button = entry.addTrailingButton(20, onClick, buttonLabelGetter, buttonColorGetter, buttonTooltip);
        appendEntry(entry);
        return new FieldWithButton(entry.field(), button);
    }

    protected XyzFieldGroup addFormXyzRow(String labelKey, @Nullable String tooltipKey,
                                          @Nullable PickState.TargetField target, boolean halfOffset) {
        XyzListEntry entry = new XyzListEntry(labelKey, tooltipKey, "X", "Y", "Z");
        BoxWidget pickBtn = null;
        if (target != null) {
            pickBtn = entry.addTrailingButton(
                20,
                () -> startPointPick(target, halfOffset),
                () -> "+",
                () -> 0x80FFFF,
                UIText.of("ponderer.ui.pick.tooltip"));
        }
        appendEntry(entry);
        return new XyzFieldGroup(entry.xField(), entry.yField(), entry.zField(), pickBtn);
    }

    protected XyzFieldGroup addFormXyzRow(String labelKey, @Nullable String tooltipKey,
                                          PickState.TargetField target) {
        return addFormXyzRow(labelKey, tooltipKey, target, false);
    }

    protected XyzFieldGroup addFormXyzRow(String labelKey, @Nullable String tooltipKey) {
        return addFormXyzRow(labelKey, tooltipKey, null, false);
    }

    protected XyzFieldGroup addFormXyzRowWithButton(String labelKey, @Nullable String tooltipKey,
                                                    Runnable onClick, Supplier<String> buttonLabelGetter,
                                                    IntSupplier buttonColorGetter,
                                                    @Nullable String buttonTooltip) {
        XyzListEntry entry = new XyzListEntry(labelKey, tooltipKey, "X", "Y", "Z");
        BoxWidget button = entry.addTrailingButton(20, onClick, buttonLabelGetter, buttonColorGetter, buttonTooltip);
        appendEntry(entry);
        return new XyzFieldGroup(entry.xField(), entry.yField(), entry.zField(), button);
    }

    protected BoxWidget addFormToggle(String labelKey, @Nullable String tooltipKey,
                                      BooleanSupplier stateGetter, Runnable onToggle) {
        ToggleListEntry entry = new ToggleListEntry(labelKey, tooltipKey, stateGetter, onToggle);
        appendEntry(entry);
        return entry.button();
    }

    protected BoxWidget addFormCycleButton(String labelKey, @Nullable String tooltipKey,
                                           int btnW, Runnable onClick,
                                           Supplier<String> labelGetter, IntSupplier colorGetter) {
        ButtonListEntry entry = new ButtonListEntry(labelKey, tooltipKey, btnW, onClick, labelGetter, colorGetter, null);
        appendEntry(entry);
        return entry.button();
    }

    protected BoxWidget addFormCycleButton(String labelKey, @Nullable String tooltipKey,
                                           int btnW, Runnable onClick, Supplier<String> labelGetter) {
        return addFormCycleButton(labelKey, tooltipKey, btnW, onClick, labelGetter, () -> 0xFFFFFF);
    }

    protected HintableTextFieldWidget addFormNumberField(String labelKey, @Nullable String tooltipKey,
                                                         String hint, int fieldW, @Nullable String unitKey) {
        PlainTextListEntry entry = new PlainTextListEntry(labelKey, tooltipKey, null, "", value -> {
        });
        entry.field().setHint(hint);
        entry.setPreferredFieldWidth(fieldW);
        if (unitKey != null) {
            entry.setUnitText(() -> UIText.of(unitKey));
        }
        appendEntry(entry);
        return entry.field();
    }

    protected HintableTextFieldWidget addFormNumberField(String labelKey, @Nullable String tooltipKey,
                                                         String hint, int fieldW) {
        return addFormNumberField(labelKey, tooltipKey, hint, fieldW, null);
    }

    protected FieldWithLang addFormTextFieldWithLang(String labelKey, @Nullable String tooltipKey,
                                                     String hint, int fieldW, Supplier<String> langGetter,
                                                     Runnable onToggle) {
        LocalizedTextListEntry entry = new LocalizedTextListEntry(labelKey, tooltipKey, null, "", langGetter, onToggle, value -> {
        });
        entry.field().setHint(hint);
        entry.setPreferredFieldWidth(fieldW);
        appendEntry(entry);
        return new FieldWithLang(entry.field(), entry.langButton());
    }

    protected DualFieldGroup addFormDualNumberField(String labelKey, @Nullable String tooltipKey,
                                                    String firstHint, int firstWidth,
                                                    String secondHint, int secondWidth) {
        DualTextListEntry entry = new DualTextListEntry(labelKey, tooltipKey, firstHint, secondHint);
        entry.setPreferredWidths(firstWidth, secondWidth);
        appendEntry(entry);
        return new DualFieldGroup(entry.firstField(), entry.secondField());
    }

    protected void addFormBlockProps(String labelKey, @Nullable String tooltipKey) {
        blockPropFields = new ArrayList<>();
        if (blockPropEntries == null || blockPropEntries.isEmpty()) {
            blockPropEntries = new ArrayList<>();
            blockPropEntries.add(new String[]{"", ""});
        }

        for (int i = 0; i < blockPropEntries.size(); i++) {
            final int idx = i;
            String[] pair = blockPropEntries.get(i);
            BlockPropertyListEntry entry = new BlockPropertyListEntry(
                i == 0 ? labelKey : "",
                i == 0 ? tooltipKey : null,
                pair[0],
                pair[1],
                () -> removeBlockPropEntry(idx));
            appendEntry(entry);
            blockPropFields.add(new HintableTextFieldWidget[]{entry.keyField(), entry.valueField()});
        }

        ButtonListEntry addEntry = new ButtonListEntry(
            "",
            null,
            40,
            this::addBlockPropEntry,
            () -> "+",
            () -> 0x80FF80,
            UIText.of("ponderer.ui.block_properties"));
        appendEntry(addEntry);
    }

    private void appendEntry(ConfigScreenList.Entry entry) {
        if (currentEntries == null) {
            throw new IllegalStateException("Step editor entries can only be appended during collectEntries()");
        }
        currentEntries.add(entry);
    }

    private void toggleJei(HintableTextFieldWidget targetField, IdFieldMode mode) {
        if (jeiActive && jeiTargetField == targetField) {
            deactivateJei();
            return;
        }
        jeiActive = true;
        jeiTargetField = targetField;
        jeiMode = mode;
        JeiCompat.setActiveEditor(this, mode);
    }

    private void startPointPick(PickState.TargetField target, boolean halfOffset) {
        Map<String, String> snapshot = snapshotForm();
        PickState.startPick(
            target,
            snapshot,
            getStepType(),
            editIndex,
            insertAfterIndex,
            scene,
            sceneIndex,
            parent,
            halfOffset);
        PickState.openPonderUIForPick();
    }

    private void startNbtPick(String nbtSnapshotKey, boolean captureBlockId) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        errorMessage = null;
        infoMessage = null;
        Map<String, String> snapshot = snapshotForm();
        NbtPickState.startPick(
            snapshot,
            nbtSnapshotKey,
            captureBlockId,
            getStepType(),
            editIndex,
            insertAfterIndex,
            scene,
            sceneIndex,
            parent);
        mc.setScreen(null);
    }

    private void useHeldItem(Consumer<ItemStack> onItemPicked) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty()) {
            held = mc.player.getOffhandItem();
        }
        if (held.isEmpty()) {
            errorMessage = UIText.of("ponderer.ui.held_item.error.empty");
            infoMessage = null;
            return;
        }
        errorMessage = null;
        infoMessage = null;
        onItemPicked.accept(held);
        infoMessage = UIText.of("ponderer.ui.nbt_pick.filled", held.getHoverName().getString());
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

    @Nullable
    @Override
    public HintableTextFieldWidget getJeiTargetField() {
        return jeiTargetField;
    }

    @Override
    public void deactivateJei() {
        jeiActive = false;
        jeiTargetField = null;
        jeiMode = null;
        JeiCompat.clearActiveEditor();
    }

    @Override
    public void showJeiIncompatibleWarning(IdFieldMode mode) {
        errorMessage = switch (mode) {
            case BLOCK -> UIText.of("ponderer.ui.jei.error.not_block");
            case ENTITY -> UIText.of("ponderer.ui.jei.error.not_spawn_egg");
            case ITEM, INGREDIENT -> null;
        };
        infoMessage = null;
    }

    @Override
    public void removed() {
        super.removed();
        if (jeiActive) {
            deactivateJei();
        }
    }

    @Nullable
    protected Double parseDouble(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            errorMessage = UIText.of("ponderer.ui.error.required_field", fieldName);
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            errorMessage = UIText.of("ponderer.ui.error.invalid_number", fieldName);
            return null;
        }
    }

    protected double parseDoubleOr(String value, double fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Nullable
    protected Float parseFloat(String value, String fieldName) {
        Double d = parseDouble(value, fieldName);
        return d == null ? null : d.floatValue();
    }

    @Nullable
    protected Integer parseInt(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            errorMessage = UIText.of("ponderer.ui.error.required_field", fieldName);
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            errorMessage = UIText.of("ponderer.ui.error.invalid_integer", fieldName);
            return null;
        }
    }

    protected int parseIntOr(String value, int fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    protected boolean usesBlockProps() {
        return false;
    }

    protected int blockPropRowCount() {
        return blockPropEntries == null ? 0 : blockPropEntries.size() + 1;
    }

    private void preExtractBlockProps() {
        if (blockPropEntries != null) {
            return;
        }

        if (pendingPickRestore != null && pendingPickRestore.containsKey("prop_count")) {
            applyBlockPropsSnapshot(pendingPickRestore);
            if (blockPropEntries != null && !blockPropEntries.isEmpty()) {
                return;
            }
        }

        if (isEditMode() && existingStep != null
            && existingStep.blockProperties != null && !existingStep.blockProperties.isEmpty()) {
            blockPropEntries = new ArrayList<>();
            for (Map.Entry<String, String> entry : existingStep.blockProperties.entrySet()) {
                blockPropEntries.add(new String[]{entry.getKey(), entry.getValue()});
            }
            return;
        }

        blockPropEntries = new ArrayList<>();
        blockPropEntries.add(new String[]{"", ""});
    }

    private void addBlockPropEntry() {
        syncBlockPropFieldsToEntries();
        if (blockPropEntries == null) {
            blockPropEntries = new ArrayList<>();
        }
        blockPropEntries.add(new String[]{"", ""});
        rebuildFormPreservingState();
    }

    private void removeBlockPropEntry(int idx) {
        syncBlockPropFieldsToEntries();
        if (blockPropEntries == null || idx < 0 || idx >= blockPropEntries.size()) {
            return;
        }
        blockPropEntries.remove(idx);
        if (blockPropEntries.isEmpty()) {
            blockPropEntries.add(new String[]{"", ""});
        }
        rebuildFormPreservingState();
    }

    protected void syncBlockPropFieldsToEntries() {
        if (blockPropFields == null || blockPropEntries == null) {
            return;
        }
        for (int i = 0; i < blockPropFields.size() && i < blockPropEntries.size(); i++) {
            blockPropEntries.get(i)[0] = blockPropFields.get(i)[0].getValue();
            blockPropEntries.get(i)[1] = blockPropFields.get(i)[1].getValue();
        }
    }

    @Nullable
    protected Map<String, String> collectBlockProperties() {
        syncBlockPropFieldsToEntries();
        if (blockPropEntries == null) {
            return null;
        }
        Map<String, String> map = new HashMap<>();
        for (String[] entry : blockPropEntries) {
            String key = entry[0].trim();
            String value = entry[1].trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                map.put(key, value);
            }
        }
        return map.isEmpty() ? null : map;
    }

    protected void snapshotBlockProps(Map<String, String> snapshot) {
        syncBlockPropFieldsToEntries();
        if (blockPropEntries == null) {
            return;
        }
        snapshot.put("prop_count", String.valueOf(blockPropEntries.size()));
        for (int i = 0; i < blockPropEntries.size(); i++) {
            snapshot.put("prop_key_" + i, blockPropEntries.get(i)[0]);
            snapshot.put("prop_val_" + i, blockPropEntries.get(i)[1]);
        }
    }

    protected void restoreBlockProps(Map<String, String> snapshot) {
        if (blockPropFields == null || !snapshot.containsKey("prop_count")) {
            return;
        }
        int count;
        try {
            count = Integer.parseInt(snapshot.get("prop_count"));
        } catch (NumberFormatException e) {
            return;
        }
        for (int i = 0; i < count && i < blockPropFields.size(); i++) {
            blockPropFields.get(i)[0].setValue(snapshot.getOrDefault("prop_key_" + i, ""));
            blockPropFields.get(i)[1].setValue(snapshot.getOrDefault("prop_val_" + i, ""));
        }
    }

    private void applyBlockPropsSnapshot(Map<String, String> snapshot) {
        if (!snapshot.containsKey("prop_count")) {
            return;
        }
        int count;
        try {
            count = Integer.parseInt(snapshot.get("prop_count"));
        } catch (NumberFormatException e) {
            return;
        }
        blockPropEntries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            blockPropEntries.add(new String[]{
                snapshot.getOrDefault("prop_key_" + i, ""),
                snapshot.getOrDefault("prop_val_" + i, "")
            });
        }
        if (blockPropEntries.isEmpty()) {
            blockPropEntries.add(new String[]{"", ""});
        }
    }
}
