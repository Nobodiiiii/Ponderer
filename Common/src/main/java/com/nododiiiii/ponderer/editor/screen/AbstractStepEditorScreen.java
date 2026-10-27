package com.nododiiiii.ponderer.editor.screen;

import com.nododiiiii.ponderer.editor.capture.PickState;
import com.nododiiiii.ponderer.editor.preview.SceneEditorPreview;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.editor.screen.widgets.DeclarativeFormEntry;
import com.nododiiiii.ponderer.editor.screen.widgets.PonderIconStencils;
import com.nododiiiii.ponderer.editor.screen.widgets.ScreenTooltipBoxWidget;
import net.createmod.catnip.gui.ConfirmationScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.List;

public abstract class AbstractStepEditorScreen extends AbstractSceneEditorFormScreen implements PointPickButtonHost {

    protected static final int STEP_EDITOR_LIST_WIDTH = UILayoutConstants.EDITOR_LIST_W;
    private static final int SIDEBAR_BUTTON_SIZE = 28;
    private static final int SIDEBAR_BUTTON_GAP = 2;

    protected final int editIndex;
    @Nullable
    protected final DslScene.DslStep existingStep;

    protected boolean attachKeyFrame = false;
    protected int insertAfterIndex = -1;
    private final SceneEditorPreview preview = new SceneEditorPreview();
    @Nullable
    private DslScene previewDraft;
    private int previewDraftStepIndex;
    @Nullable
    private BoxWidget previousStepButton;
    @Nullable
    private BoxWidget nextStepButton;

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
            STEP_EDITOR_LIST_WIDTH);
        this.editIndex = editIndex;
        this.existingStep = existingStep;
        preview.setStepPreviewAction(this::previewCurrentStep);
        if (parent != null) preview.restoreView(parent.previewViewState());
    }

    @Override
    protected void init() {
        super.init();
        initStepActionButtons();
        reloadStepPreview();
        preview.layoutOnLeft(width, height);
    }

    private void initStepActionButtons() {
        if (saveChanges == null) return;

        int x = saveChanges.getX();
        int spacing = SIDEBAR_BUTTON_SIZE + SIDEBAR_BUTTON_GAP;
        int top = height / 2 - (SIDEBAR_BUTTON_SIZE * 5 + SIDEBAR_BUTTON_GAP * 4) / 2;
        previousStepButton = addStepNavigationButton(x, top, -1, PonderGuiTextures.ICON_PONDER_LEFT,
            "ponderer.ui.step_editor.previous");
        nextStepButton = addStepNavigationButton(x, top + spacing, 1, PonderGuiTextures.ICON_PONDER_RIGHT,
            "ponderer.ui.step_editor.next");
        saveChanges.setY(top + spacing * 2);
        if (discardChanges != null) discardChanges.setY(top + spacing * 3);
        if (goBack != null) goBack.setY(top + spacing * 4);
    }

    private void reloadStepPreview() {
        preview.load(previewDraft != null ? previewDraft : scene, sceneIndex);
        preview.seekStep(previewDraft != null ? previewDraftStepIndex
            : isEditMode() ? editIndex : Math.max(0, insertAfterIndex));
    }

    private void previewCurrentStep() {
        preview.cancelPointPick();
        clearStatusMessages();
        DslScene.DslStep step = buildStep();
        if (step == null) return;
        if (attachKeyFrame) step.attachKeyFrame = true;

        // Compile an isolated draft so previewing cannot save or modify the scene's undo history.
        DslScene draft = SceneStore.copyScene(scene);
        if (draft == null || draft.scenes == null || sceneIndex < 0 || sceneIndex >= draft.scenes.size()) {
            setErrorMessage(UIText.of("ponderer.ui.scene_editor.preview_unavailable"));
            return;
        }
        DslScene.SceneSegment segment = draft.scenes.get(sceneIndex);
        List<DslScene.DslStep> steps = segment.steps == null
            ? new java.util.ArrayList<>() : new java.util.ArrayList<>(segment.steps);
        segment.steps = steps;
        if (isEditMode()) {
            if (editIndex >= steps.size()) {
                setErrorMessage(UIText.of("ponderer.ui.scene_editor.preview_unavailable"));
                return;
            }
            steps.set(editIndex, step);
        } else {
            int index = insertAfterIndex >= 0 && insertAfterIndex < steps.size()
                ? insertAfterIndex + 1 : steps.size();
            steps.add(index, step);
        }
        // Sanitization may prepend scene setup when this is the first step being added.
        SceneStore.sanitizeScene(draft);
        int index = segment.steps.indexOf(step);
        if (!preview.load(draft, sceneIndex)) {
            setErrorMessage(UIText.of("ponderer.ui.scene_editor.preview_unavailable"));
            return;
        }
        previewDraft = draft;
        previewDraftStepIndex = index;
        preview.playStep(index);
        setInfoMessage(UIText.of("ponderer.ui.step_editor.preview.updated"));
    }

    @Override
    protected void discardEdits() {
        preview.cancelPointPick();
        super.discardEdits();
        previewDraft = null;
        reloadStepPreview();
    }

    private BoxWidget addStepNavigationButton(int x, int y, int direction, PonderGuiTextures icon, String tooltipKey) {
        BoxWidget button = new ScreenTooltipBoxWidget(x, y, SIDEBAR_BUTTON_SIZE, SIDEBAR_BUTTON_SIZE)
            .withPadding(6, 6)
            .withCallback(() -> attemptStepNavigation(direction));
        PonderIconStencils.attach(button, PonderIconStencils.rotated(icon, 90));
        setActionButtonTooltip(button, tooltipKey);
        button.active = createAdjacentStepEditor(direction) != null;
        button.updateGradientFromState();
        addRenderableWidget(button);
        return button;
    }

    @Nullable
    private AbstractStepEditorScreen createAdjacentStepEditor(int direction) {
        if (!isEditMode() || parent == null) return null;

        List<DslScene.DslStep> steps = StepEditorContext.getStepsForScene(scene, sceneIndex);
        if (steps == null) return null;

        for (int index = editIndex + direction; index >= 0 && index < steps.size(); index += direction) {
            AbstractStepEditorScreen editor = StepEditorFactory.createEditScreen(
                steps.get(index), index, scene, sceneIndex, parent);
            if (editor != null) return editor;
        }
        return null;
    }

    private void attemptStepNavigation(int direction) {
        if (!hasUnsavedChanges()) {
            openAdjacentStep(direction);
            return;
        }

        showLeavingPrompt(response -> {
            if (response == ConfirmationScreen.Response.Cancel) return;
            if (response == ConfirmationScreen.Response.Confirm) {
                if (!saveEdits()) return;
            } else {
                discardEdits();
            }
            openAdjacentStep(direction);
        }, "ponderer.ui.step_editor.unsaved_changes.message",
            "ponderer.ui.step_editor.unsaved_changes.save_and_switch",
            "ponderer.ui.step_editor.unsaved_changes.discard_and_switch");
    }

    private void openAdjacentStep(int direction) {
        // Saving an interface step can remove dependent steps, so resolve the target after saving.
        AbstractStepEditorScreen editor = createAdjacentStepEditor(direction);
        if (editor == null) {
            updateButtonState(direction < 0 ? previousStepButton : nextStepButton, false);
            return;
        }

        editor.preview.restoreView(preview.viewState());
        Minecraft.getInstance().setScreen(editor);
    }

    private int formLeft() {
        return width * 2 / 5 + 8;
    }

    @Override
    protected int listHorizontalOffset() {
        return formLeft() - (width / 2 - currentListWidthValue() / 2);
    }

    @Override
    protected int availableListWidth() {
        return Math.max(1, width - formLeft() - 40);
    }

    @Override
    protected int breadcrumbCenterX() {
        return formLeft() + currentListWidthValue() / 2;
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderWindow(graphics, mouseX, mouseY, partialTicks);
        preview.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);
        preview.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && cancelPointPick()) return true;
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        return !(getFocused() instanceof EditBox) && !hasControlDown() && !hasAltDown()
            && preview.keyPressed(keyCode);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return preview.keyReleased(keyCode) || super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        preview.tick();
    }

    @Override
    public void removed() {
        cancelPointPick();
        if (parent != null) parent.restorePreviewView(preview.viewState());
        preview.close();
        super.removed();
    }

    @Override
    public void onClose() {
        if (!cancelPointPick()) super.onClose();
    }

    private boolean cancelPointPick() {
        if (!preview.cancelPointPick()) return false;
        clearStatusMessages();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Give form popups first refusal, including clicks outside the inventory picker.
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (!preview.mouseClicked(mouseX, mouseY, button)) return false;
        setFocused(null);
        setDragging(button == 0);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return preview.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
            || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (preview.mouseReleased(mouseX, mouseY, button)) {
            setDragging(false);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
            || preview.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public int getGuiLeft() {
        // JEI derives the bookmark area's width from guiLeft, before applying exclusions.
        return formLeft();
    }

    @Override
    public int getGuiTop() {
        return 0;
    }

    @Override
    public int getGuiWidth() {
        // Split bookmarks and ingredients at the form's left edge. Actual widget
        // exclusions let JEI also use the free space above and below the form and preview.
        return 1;
    }

    @Override
    public int getGuiHeight() {
        return height;
    }

    @Override
    public List<Rect2i> getJeiExtraAreas() {
        List<Rect2i> areas = new java.util.ArrayList<>(super.getJeiExtraAreas());
        areas.addAll(occupiedWidgetAreas());
        areas.add(preview.occupiedArea());
        return areas;
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
        PickState.TargetField effectiveTarget = resolvePointPickTarget(target);
        if (effectiveTarget != PickState.TargetField.UI_POINT) {
            SceneEditorPreview.PointPickMode mode;
            if (halfOffset || target == PickState.TargetField.POINT || target == PickState.TargetField.LOOK_AT) {
                mode = SceneEditorPreview.PointPickMode.FACE_CENTER;
            } else if ("set_block".equals(getStepType()) || "show_extra_structure".equals(getStepType())) {
                mode = SceneEditorPreview.PointPickMode.ADJACENT_BLOCK;
            } else {
                mode = SceneEditorPreview.PointPickMode.BLOCK;
            }
            clearStatusMessages();
            if (preview.startPointPick(mode, point -> applyPickedPoint(effectiveTarget, point))) {
                setFocused(null);
                setInfoMessage(UIText.of("ponderer.ui.pick.preview.active"));
            } else {
                setErrorMessage(UIText.of("ponderer.ui.scene_editor.preview_unavailable"));
            }
            return;
        }
        PickState.startPick(
            effectiveTarget,
            snapshotForm(),
            getStepType(),
            editIndex,
            insertAfterIndex,
            scene,
            sceneIndex,
            parent,
            effectiveTarget == PickState.TargetField.UI_POINT ? false : halfOffset);
        PickState.openPonderUIForPick();
    }

    private void applyPickedPoint(PickState.TargetField target, Vec3 point) {
        String prefix = switch (target) {
            case POS2 -> "pos2";
            case LOOK_AT -> "lookAt";
            case POINT, UI_POINT -> "point";
            default -> "pos";
        };
        java.util.Map<String, String> snapshot = snapshotForm();
        snapshot.put(prefix + "X", formatPickedCoordinate(point.x));
        snapshot.put(prefix + "Y", formatPickedCoordinate(point.y));
        snapshot.put(prefix + "Z", formatPickedCoordinate(point.z));
        restoreFromSnapshot(snapshot);
        setInfoMessage(UIText.of("ponderer.ui.pick.preview.filled"));
    }

    private static String formatPickedCoordinate(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value)) : Double.toString(value);
    }

    private PickState.TargetField resolvePointPickTarget(PickState.TargetField target) {
        if (target != PickState.TargetField.POINT || !isInterfaceStartScene()) {
            return target;
        }
        return PickState.TargetField.UI_POINT;
    }

    @Override
    public FieldType pointPickFieldType(PickState.TargetField target, boolean halfOffset) {
        return PointPickButtonHost.super.pointPickFieldType(resolvePointPickTarget(target), halfOffset);
    }

    @Override
    public String pointPickTooltip(PickState.TargetField target) {
        return UIText.of(resolvePointPickTarget(target) == PickState.TargetField.UI_POINT
            ? "ponderer.ui.pick.ui.tooltip" : "ponderer.ui.pick.tooltip");
    }

    private boolean isInterfaceStartScene() {
        if (scene.scenes == null || scene.scenes.isEmpty()) {
            return false;
        }
        if (sceneIndex < 0 || sceneIndex >= scene.scenes.size()) {
            return false;
        }

        List<DslScene.DslStep> steps = scene.scenes.get(sceneIndex).steps;
        if (steps == null) {
            return false;
        }

        for (DslScene.DslStep step : steps) {
            if (step == null || step.type == null || step.type.isBlank()) {
                continue;
            }
            return "show_interface".equalsIgnoreCase(step.type);
        }
        return false;
    }

    private boolean saveAndClose() {
        if (!saveEdits()) {
            return false;
        }
        // Save always lands back on the scene editor, even when this screen was opened from
        // the step type selector — that way the editor's remembered list scroll is restored.
        ScreenOpener.open(parent);
        return true;
    }
}
