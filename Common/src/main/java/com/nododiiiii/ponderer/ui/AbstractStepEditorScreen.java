package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeFormScreen;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
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
import java.util.function.Consumer;

public abstract class AbstractStepEditorScreen extends AbstractDeclarativeFormScreen implements JeiTextButtonHost {

    protected static final int STEP_EDITOR_LIST_WIDTH = UILayoutConstants.EDITOR_LIST_W;

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

    private boolean initialPopulateDone = false;
    @Nullable
    private Map<String, String> pendingPickRestore = null;
    @Nullable
    private Map<String, String> baselineSnapshot = null;
    private final List<SnapshotParticipant> formStateParticipants = new ArrayList<>();
    private boolean formStateParticipantsConfigured = false;

    private boolean jeiActive = false;
    @Nullable
    private HintableTextFieldWidget jeiTargetField = null;
    @Nullable
    private IdFieldMode jeiMode = null;

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
        ensureFormStateParticipants();
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
    protected final void collectFormEntries(List<DeclarativeFormEntry> entries) {
        collectStepEntries(entries);
        if (showsKeyFrame()) {
            entries.add(FieldSpecs.toggle(
                "ponderer.ui.key_frame",
                "ponderer.ui.key_frame.tooltip",
                () -> attachKeyFrame,
                () -> attachKeyFrame = !attachKeyFrame));
        }
    }

    protected abstract void collectStepEntries(List<DeclarativeFormEntry> entries);

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

    protected void populateFromStep(DslScene.DslStep step) {
        attachKeyFrame = Boolean.TRUE.equals(step.attachKeyFrame);
    }

    @Nullable
    protected abstract DslScene.DslStep buildStep();

    protected abstract String getHeaderTitle();

    protected abstract String getStepType();

    protected void configureFormState(List<SnapshotParticipant> participants) {
    }

    protected final Map<String, String> snapshotForm() {
        Map<String, String> snapshot = new HashMap<>();
        for (DeclarativeFormEntry entry : builtFormEntries()) {
            entry.snapshot(snapshot);
        }
        FormState.snapshotOf(formStateParticipants).forEach(snapshot::put);
        appendCustomSnapshot(snapshot);
        return snapshot;
    }

    protected final void restoreFromSnapshot(Map<String, String> snapshot) {
        FormState.restoreInto(snapshot, formStateParticipants);
        for (DeclarativeFormEntry entry : builtFormEntries()) {
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
        FormState.restoreInto(snapshot, formStateParticipants);
        restoreCustomSnapshot(snapshot);
    }

    private void ensureFormStateParticipants() {
        if (formStateParticipantsConfigured) {
            return;
        }
        formStateParticipantsConfigured = true;
        formStateParticipants.add(FieldBindings.bool("_keyFrame", () -> attachKeyFrame, value -> attachKeyFrame = value));
        configureFormState(formStateParticipants);
    }

    @Override
    public void toggleJeiForField(HintableTextFieldWidget targetField, IdFieldMode mode) {
        if (jeiActive && jeiTargetField == targetField) {
            deactivateJei();
            return;
        }
        jeiActive = true;
        jeiTargetField = targetField;
        jeiMode = mode;
        JeiCompat.setActiveEditor(this, mode);
    }

    @Override
    public boolean isJeiActiveForField(HintableTextFieldWidget field) {
        return jeiActive && jeiTargetField == field;
    }

    protected final void startPointPickFromButton(PickState.TargetField target, boolean halfOffset) {
        startPointPick(target, halfOffset);
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

    protected final void startNbtPickFromButton(String nbtSnapshotKey, boolean captureBlockId) {
        startNbtPick(nbtSnapshotKey, captureBlockId);
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

    protected final void useHeldItemFromButton(Consumer<ItemStack> onItemPicked) {
        useHeldItem(onItemPicked);
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

}
