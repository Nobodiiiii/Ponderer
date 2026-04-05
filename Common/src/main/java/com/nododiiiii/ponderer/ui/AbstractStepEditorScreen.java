package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;
import java.util.List;

public abstract class AbstractStepEditorScreen extends AbstractSceneEditorFormScreen implements PointPickButtonHost {

    protected static final int STEP_EDITOR_LIST_WIDTH = UILayoutConstants.EDITOR_LIST_W;

    protected final int editIndex;
    @Nullable
    protected final DslScene.DslStep existingStep;

    protected boolean attachKeyFrame = false;
    protected int insertAfterIndex = -1;

    protected static final java.util.Map<String, Integer> PALETTE_COLORS = java.util.Map.ofEntries(
        java.util.Map.entry("white", 0xEEEEEE),
        java.util.Map.entry("black", 0x221111),
        java.util.Map.entry("red", 0xFF5D6C),
        java.util.Map.entry("green", 0x8CBA51),
        java.util.Map.entry("blue", 0x5F6CAF),
        java.util.Map.entry("slow", 0x22FF22),
        java.util.Map.entry("medium", 0x0084FF),
        java.util.Map.entry("fast", 0xFF55FF),
        java.util.Map.entry("input", 0x7FCDE0),
        java.util.Map.entry("output", 0xDDC166));

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
        super(scene, sceneIndex, parent, "ponderer.ui.scope.editor", "ponderer.ui.step_editor",
            STEP_EDITOR_LIST_WIDTH, (screen, mode) -> JeiCompat.setActiveEditor((AbstractStepEditorScreen) screen, mode));
        this.editIndex = editIndex;
        this.existingStep = existingStep;
    }

    protected boolean isEditMode() {
        return editIndex >= 0 && existingStep != null;
    }

    @Override
    public AbstractStepEditorScreen setReturnScreen(@Nullable Screen returnScreen) {
        super.setReturnScreen(returnScreen);
        return this;
    }

    public AbstractStepEditorScreen setInsertAfterIndex(int index) {
        this.insertAfterIndex = index;
        return this;
    }

    public AbstractStepEditorScreen setPendingPickRestore(@Nullable java.util.Map<String, String> snapshot) {
        setPendingFormRestore(snapshot);
        return this;
    }

    @Override
    protected void prepareInitialState() {
        if (isEditMode()) {
            populateFromStep(existingStep);
        }
    }

    @Override
    protected void configureActionButtons() {
        if (saveChanges != null) {
            saveChanges.withCallback(this::saveAndClose);
        }
    }

    @Override
    protected boolean isSaveButtonActive() {
        return !isEditMode() || super.isSaveButtonActive();
    }

    @Override
    protected void addBaseFormStateParticipants(List<SnapshotParticipant> participants) {
        participants.add(FieldBindings.bool("_keyFrame", () -> attachKeyFrame, value -> attachKeyFrame = value));
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
    protected boolean saveEdits() {
        clearStatusMessages();

        DslScene.DslStep step = buildStep();
        if (step == null) {
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

        markStateSaved();
        return true;
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

    @Override
    protected SnapshotReturnContext createReturnContext() {
        return new StepEditorContext(getStepType(), editIndex, insertAfterIndex, scene, sceneIndex, parent);
    }

    @Override
    public final void startPointPickFromButton(PickState.TargetField target, boolean halfOffset) {
        PickState.startPick(
            target,
            snapshotForm(),
            getStepType(),
            editIndex,
            insertAfterIndex,
            scene,
            sceneIndex,
            parent,
            halfOffset);
        PickState.openPonderUIForPick();
    }

    private boolean saveAndClose() {
        if (!saveEdits()) {
            return false;
        }
        returnToParent();
        return true;
    }
}
