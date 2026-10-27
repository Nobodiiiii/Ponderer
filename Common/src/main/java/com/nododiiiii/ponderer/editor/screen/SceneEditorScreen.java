package com.nododiiiii.ponderer.editor.screen;

import com.nododiiiii.ponderer.editor.preview.SceneEditorPreview;
import com.nododiiiii.ponderer.editor.session.UndoManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.scene.LocalizedText;
import com.nododiiiii.ponderer.editor.screen.widgets.AbstractDeclarativeListScreen;
import com.nododiiiii.ponderer.editor.screen.widgets.ActionStripListEntry;
import com.nododiiiii.ponderer.editor.screen.widgets.PonderIconStencils;
import com.nododiiiii.ponderer.editor.screen.widgets.PondererGuiIcons;
import com.nododiiiii.ponderer.editor.screen.widgets.ScreenTooltipBoxWidget;
import com.nododiiiii.ponderer.editor.screen.widgets.SceneStepListEntry;
import com.nododiiiii.ponderer.editor.screen.widgets.SectionHeaderListEntry;
import com.nododiiiii.ponderer.editor.screen.widgets.WorkspaceHeaderListEntry;
import net.createmod.catnip.gui.ConfirmationScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SceneEditorScreen extends AbstractDeclarativeListScreen {

    private static final Gson STEP_GSON = new GsonBuilder()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter())
        .create();
    private static final int SIDEBAR_BUTTON_SIZE = 28;
    private static final int SIDEBAR_BUTTON_GAP = 2;
    private static final int STEP_ACTION_ICON_SIZE = 12;
    private static final int SCENE_LIST_WIDTH = 400;
    private static final com.nododiiiii.ponderer.editor.session.EditorHistory HISTORY_SESSION = new com.nododiiiii.ponderer.editor.session.EditorHistory();

    private static List<DslScene.DslStep> clipboard = List.of();
    @Nullable
    private static PonderScreenNavigation.ReturnState pendingPonderReturnState = null;

    private final DslScene scene;
    private final com.nododiiiii.ponderer.editor.session.EditorSession session;
    @Nullable
    private final PonderScreenNavigation.ReturnState ponderReturnState;
    private int sceneIndex;
    private final Map<DslScene.DslStep, DslScene.DslStep> copiedSteps = new IdentityHashMap<>();
    private final SceneEditorPreview preview = new SceneEditorPreview();
    private boolean syncingListFromPreview;

    @Nullable
    private BoxWidget undoButton;
    @Nullable
    private BoxWidget redoButton;

    public SceneEditorScreen(DslScene scene, int sceneIndex) {
        this(scene, sceneIndex, consumePendingPonderReturnState());
    }

    private SceneEditorScreen(DslScene scene, int sceneIndex,
                              @Nullable PonderScreenNavigation.ReturnState ponderReturnState) {
        super(null, "ponderer.ui.scope.editor", "ponderer.ui.scene_editor", SCENE_LIST_WIDTH);
        this.session = new com.nododiiiii.ponderer.editor.session.EditorSession(scene, sceneIndex);
        this.scene = session.draft();
        this.sceneIndex = sceneIndex;
        this.ponderReturnState = ponderReturnState;
    }

    public static boolean canModifyScene(DslScene scene) {
        return scene != null && (scene.isEditable(Config.DEFAULT_EDITABLE.get()) || Config.DEVELOPER_MODE.get());
    }

    @Override
    protected void init() {
        super.init();
        configureActionButtons();
        initHistoryButtons();
        preview.load(scene, sceneIndex);
        layoutPreview();
        restoreRememberedListScroll();
    }

    @Override
    protected int listHorizontalOffset() {
        return listLeftEdge() - (width / 2 - currentListWidthValue() / 2);
    }

    @Override
    protected int availableListWidth() {
        return Math.max(1, width - listLeftEdge() - 40);
    }

    private int listLeftEdge() {
        return width * 2 / 5 + 8;
    }

    @Override
    protected int listBottomPadding(int viewportHeight) {
        // Let even the final step reach the top, including in lists shorter than the viewport.
        return Math.max(0, viewportHeight - getEntryHeight());
    }

    @Override
    protected void onListScrollChanged(int firstVisibleIndex) {
        if (!syncingListFromPreview) preview.seekStep(firstVisibleIndex);
    }

    private void layoutPreview() {
        preview.layoutOnLeft(width, height);
    }

    SceneEditorPreview.ViewState previewViewState() {
        return preview.viewState();
    }

    void restorePreviewView(SceneEditorPreview.ViewState state) {
        preview.restoreView(state);
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
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return preview.keyReleased(keyCode) || super.keyReleased(keyCode, scanCode, modifiers);
    }

    private void scrollListToPreview() {
        if (list == null) return;
        syncingListFromPreview = true;
        try {
            list.setScrollAmount(preview.stepAtCursor() * (double) getEntryHeight() + 7);
        } finally {
            syncingListFromPreview = false;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (preview.mouseClicked(mouseX, mouseY, button)) {
            setFocused(null);
            setDragging(button == 0);
            if (preview.isScrubbing()) scrollListToPreview();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (preview.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            if (preview.isScrubbing()) scrollListToPreview();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
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
        return preview.mouseScrolled(mouseX, mouseY, scrollY)
            || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void removed() {
        preview.close();
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();
        preview.tick();
        refreshHistoryButtonState();
    }

    @Override
    protected void collectHeaderEntries(List<net.createmod.catnip.config.ui.ConfigScreenList.Entry> entries) {
        entries.add(new WorkspaceHeaderListEntry(
            this::screenTitleText,
            this::screenSubtitleText,
            List.of(
                WorkspaceHeaderListEntry.iconButton(
                    PonderIconStencils.centered(PonderGuiTextures.ICON_PONDER_LEFT),
                    () -> switchScene(sceneIndex - 1),
                    null,
                    () -> getSceneCount() > 1),
                WorkspaceHeaderListEntry.iconButton(
                    PonderIconStencils.centered(PonderGuiTextures.ICON_PONDER_RIGHT),
                    () -> switchScene(sceneIndex + 1),
                    null,
                    () -> getSceneCount() > 1))));

        entries.add(new ActionStripListEntry(List.of(
            ActionStripListEntry.button(
                () -> UIText.of("ponderer.ui.scene_editor.desc"),
                null,
                this::openSceneDescription,
                () -> 0xFFFFFF,
                () -> true),
            ActionStripListEntry.button(
                () -> UIText.of("ponderer.ui.scene_editor.trigger"),
                null,
                this::openTriggerEditor,
                () -> 0xFFFFFF,
                () -> true),
            ActionStripListEntry.button(
                () -> UIText.of("ponderer.ui.scene_editor.split"),
                null,
                this::openSplitTypeSelector,
                () -> 0xFFFFFF,
                () -> true),
            ActionStripListEntry.button(
                () -> UIText.of("ponderer.ui.scene_editor.delete_scene"),
                null,
                this::confirmDeleteScene,
                () -> 0xFFFF8080,
                () -> true))));
    }

    @Override
    protected void collectEntries(List<net.createmod.catnip.config.ui.ConfigScreenList.Entry> entries) {
        List<DslScene.DslStep> steps = getSteps();
        copiedSteps.keySet().retainAll(steps);
        if (steps.isEmpty()) {
            entries.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.scene_editor.no_steps")));
            return;
        }

        for (int i = 0; i < steps.size(); i++) {
            int stepIndex = i;
            entries.add(new SceneStepListEntry(
                () -> (stepIndex + 1) + ". " + StepPreviewText.title(steps.get(stepIndex)),
                () -> StepPreviewText.summary(steps.get(stepIndex)),
                () -> openEditorForStep(stepIndex),
                List.of(
                    SceneStepListEntry.iconButton(
                        PonderIconStencils.rotated(PonderGuiTextures.ICON_CONFIG_PREV, 90, STEP_ACTION_ICON_SIZE),
                        () -> List.of(net.minecraft.network.chat.Component.literal(UIText.of("ponderer.ui.scene_editor.btn.move_up"))),
                        () -> moveStepUp(stepIndex),
                        null,
                        () -> !isActionDisabled(0, stepIndex)),
                    SceneStepListEntry.iconButton(
                        PonderIconStencils.rotated(PonderGuiTextures.ICON_CONFIG_NEXT, 90, STEP_ACTION_ICON_SIZE),
                        () -> List.of(net.minecraft.network.chat.Component.literal(UIText.of("ponderer.ui.scene_editor.btn.move_down"))),
                        () -> moveStepDown(stepIndex),
                        null,
                        () -> !isActionDisabled(1, stepIndex)),
                    SceneStepListEntry.iconButton(
                        PondererGuiIcons.INSERT,
                        () -> List.of(net.minecraft.network.chat.Component.literal(UIText.of("ponderer.ui.scene_editor.btn.insert"))),
                        () -> openInsertAfter(stepIndex),
                        null,
                        () -> !isActionDisabled(2, stepIndex)),
                    SceneStepListEntry.iconButton(
                        PondererGuiIcons.COPY,
                        this::copyButtonTooltip,
                        () -> copyStep(stepIndex),
                        () -> toggleStepCopy(stepIndex),
                        () -> true,
                        copiedSteps.containsKey(steps.get(stepIndex))),
                    SceneStepListEntry.iconButton(
                        PondererGuiIcons.PASTE,
                        this::pasteButtonTooltip,
                        () -> pasteAfter(stepIndex),
                        null,
                        () -> !clipboard.isEmpty()),
                    SceneStepListEntry.iconButton(
                        PondererGuiIcons.REMOVE,
                        () -> List.of(net.minecraft.network.chat.Component.literal(UIText.of("ponderer.ui.scene_editor.btn.delete"))),
                        () -> removeStepAndSave(stepIndex),
                        null,
                        () -> !isActionDisabled(5, stepIndex)))));
        }
    }

    @Override
    protected boolean hasUnsavedChanges() {
        return false;
    }

    @Override
    protected int getUnsavedChangeCount() {
        return 0;
    }

    @Override
    protected boolean saveEdits() {
        reloadAndExit();
        return true;
    }

    @Override
    protected void discardEdits() {
    }

    @Override
    protected boolean isSaveButtonActive() {
        return true;
    }

    @Override
    protected boolean isDiscardButtonActive() {
        return true;
    }

    @Override
    protected int getEntryHeight() {
        return UILayoutConstants.LIST_ENTRY_H;
    }

    @Override
    protected void attemptBackToParent() {
        cancelAndExit();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Screen.hasControlDown()) {
            if (keyCode == GLFW.GLFW_KEY_Z) {
                performUndo();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_Y) {
                performRedo();
                return true;
            }
        }
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        return !(getFocused() instanceof EditBox) && !hasControlDown() && !hasAltDown()
            && preview.keyPressed(keyCode);
    }

    private void configureActionButtons() {
        if (saveChanges != null) {
            saveChanges.withCallback(this::saveEdits);
            setActionButtonTooltip(saveChanges, "ponderer.ui.save");
        }
        if (discardChanges != null) {
            discardChanges.withCallback(this::cancelAndExit);
            setActionButtonTooltip(discardChanges, "ponderer.ui.cancel");
        }
        if (goBack != null) {
            goBack.withCallback(this::reloadAndExit);
            goBack.visible = true;
            goBack.active = true;
            setActionButtonTooltip(goBack, "ponderer.ui.back", "ponderer.ui.scene_editor.back.tooltip");
        }
        relayoutSidebarButtons();
    }

    private void initHistoryButtons() {
        int actionX = actionButtonX();
        undoButton = createHistoryButton(
            actionX,
            0,
            this::performUndo,
            PonderIconStencils.mirrored(PonderGuiTextures.ICON_CONFIG_RESET),
            "ponderer.ui.scene_editor.undo",
            "ponderer.ui.scene_editor.undo.tooltip");
        redoButton = createHistoryButton(
            actionX,
            0,
            this::performRedo,
            PonderIconStencils.centered(PonderGuiTextures.ICON_CONFIG_RESET),
            "ponderer.ui.scene_editor.redo",
            "ponderer.ui.scene_editor.redo.tooltip");
        relayoutSidebarButtons();
        refreshHistoryButtonState();
    }

    private int actionButtonX() {
        return width / 2 + currentListWidthValue() / 2 + listHorizontalOffset() + 6;
    }

    private BoxWidget createHistoryButton(int x, int y, Runnable callback,
                                          net.createmod.catnip.gui.element.DelegatedStencilElement icon,
                                          String tooltipTitleKey, String tooltipBodyKey) {
        BoxWidget button = new ScreenTooltipBoxWidget(x, y, SIDEBAR_BUTTON_SIZE, SIDEBAR_BUTTON_SIZE)
            .withPadding(6, 6)
            .withCallback(callback);
        PonderIconStencils.attach(button, icon);
        setActionButtonTooltip(button, tooltipTitleKey, tooltipBodyKey);
        button.updateGradientFromState();
        addRenderableWidget(button);
        return button;
    }

    private void relayoutSidebarButtons() {
        int actionX = actionButtonX();
        int totalHeight = SIDEBAR_BUTTON_SIZE * 5 + SIDEBAR_BUTTON_GAP * 4;
        int currentY = height / 2 - totalHeight / 2;
        currentY = placeSidebarButton(undoButton, actionX, currentY);
        currentY = placeSidebarButton(redoButton, actionX, currentY);
        currentY = placeSidebarButton(saveChanges, actionX, currentY);
        currentY = placeSidebarButton(discardChanges, actionX, currentY);
        placeSidebarButton(goBack, actionX, currentY);
    }

    private int placeSidebarButton(@Nullable BoxWidget button, int x, int y) {
        if (button != null) {
            button.setX(x);
            button.setY(y);
            button.setWidth(SIDEBAR_BUTTON_SIZE);
            button.setHeight(SIDEBAR_BUTTON_SIZE);
        }
        return y + SIDEBAR_BUTTON_SIZE + SIDEBAR_BUTTON_GAP;
    }

    private void refreshHistoryButtonState() {
        UndoManager history = undoManager();
        updateButtonState(undoButton, history.canUndo());
        updateButtonState(redoButton, history.canRedo());
    }

    public static void markUiToEditorTransition(DslScene scene) {
        if (scene == null) {
            return;
        }
        HISTORY_SESSION.markUiToEditorTransition(historySessionKey(scene));
        pendingPonderReturnState = PonderScreenNavigation.captureReturnState();
    }

    @Nullable
    private static PonderScreenNavigation.ReturnState consumePendingPonderReturnState() {
        PonderScreenNavigation.ReturnState state = pendingPonderReturnState;
        pendingPonderReturnState = null;
        return state;
    }

    public static void handlePonderUiRemoved(@Nullable DslScene scene) {
        if (scene == null) {
            HISTORY_SESSION.clear();
            return;
        }
        HISTORY_SESSION.handlePonderUiRemoved(historySessionKey(scene));
    }

    public static void handlePonderUiFocusChanged(@Nullable DslScene scene) {
        if (scene == null) {
            HISTORY_SESSION.clear();
            return;
        }
        HISTORY_SESSION.clearIfLeavingPonder(historySessionKey(scene));
    }

    private UndoManager undoManager() {
        return HISTORY_SESSION.historyFor(historySessionKey(), currentSceneHistoryKey());
    }

    private void clearCurrentPonderHistory() {
        HISTORY_SESSION.clearPonder(historySessionKey());
    }

    private String historySessionKey() {
        return historySessionKey(scene);
    }

    private static String historySessionKey(DslScene scene) {
        String key = scene.sceneKey();
        if (key == null) {
            key = "<unknown>";
        }
        if (scene.nbtFilter != null && !scene.nbtFilter.isBlank()) {
            return key + "|nbt:" + scene.nbtFilter;
        }
        return key;
    }

    private String currentSceneHistoryKey() {
        if (scene.scenes != null && sceneIndex >= 0 && sceneIndex < scene.scenes.size()) {
            DslScene.SceneSegment segment = scene.scenes.get(sceneIndex);
            if (segment != null && segment.id != null && !segment.id.isBlank()) {
                return "segment:" + segment.id;
            }
        }
        return "index:" + sceneIndex;
    }

    private int getSceneCount() {
        if (scene.scenes != null && !scene.scenes.isEmpty()) {
            return scene.scenes.size();
        }
        return getScenes().size();
    }

    private void switchScene(int newIndex) {
        int count = getSceneCount();
        if (count <= 1) {
            return;
        }
        if (newIndex < 0) {
            newIndex = count - 1;
        }
        if (newIndex >= count) {
            newIndex = 0;
        }
        sceneIndex = newIndex;
        session.selectSegment(newIndex);
        copiedSteps.clear();
        clearStatusMessages();
        rebuildListAtTop();
        preview.load(scene, sceneIndex);
        preview.seekStep(0);
    }

    private void openSceneDescription() {
        rememberCurrentListScroll();
        ScreenOpener.open(new SceneDescEditorScreen(scene, sceneIndex, this));
    }

    private void openTriggerEditor() {
        rememberCurrentListScroll();
        ScreenOpener.open(new TriggerEditorScreen(scene, sceneIndex, this));
    }

    private void openEditorForStep(int index) {
        List<DslScene.DslStep> steps = getSteps();
        if (index < 0 || index >= steps.size()) {
            return;
        }
        rememberCurrentListScroll();
        AbstractStepEditorScreen editor = StepEditorFactory.createEditScreen(steps.get(index), index, scene, sceneIndex, this);
        if (editor != null) {
            ScreenOpener.open(editor);
        }
    }

    private static DslScene.DslStep deepCopy(DslScene.DslStep step) {
        return STEP_GSON.fromJson(STEP_GSON.toJson(step), DslScene.DslStep.class);
    }

    private boolean isActionDisabled(int actionId, int rowIndex) {
        List<DslScene.DslStep> steps = getSteps();
        if (steps.isEmpty()) {
            return false;
        }

        DslScene.DslStep first = steps.get(0);
        if (first == null || first.type == null) {
            return false;
        }
        boolean firstIsSceneStart = "show_structure".equalsIgnoreCase(first.type)
            || "show_interface".equalsIgnoreCase(first.type);
        if (!firstIsSceneStart) {
            return false;
        }

        if (rowIndex == 0 && (actionId == 5 || actionId == 0 || actionId == 1)) {
            return true;
        }
        if (rowIndex == 1 && actionId == 0) {
            return true;
        }
        return false;
    }

    private List<net.minecraft.network.chat.Component> copyButtonTooltip() {
        return List.of(
            Component.literal(UIText.of("ponderer.ui.scene_editor.btn.copy")),
            Component.literal(UIText.of("ponderer.ui.scene_editor.btn.copy.multi_select"))
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private List<net.minecraft.network.chat.Component> pasteButtonTooltip() {
        return List.of(net.minecraft.network.chat.Component.literal(UIText.of("ponderer.ui.scene_editor.btn.paste_after")));
    }

    private void copyStep(int index) {
        List<DslScene.DslStep> steps = getSteps();
        if (index >= 0 && index < steps.size()) {
            copiedSteps.clear();
            DslScene.DslStep step = steps.get(index);
            copiedSteps.put(step, deepCopy(step));
            updateClipboardFromSelection();
            rebuildListPreservingScroll();
        }
    }

    private void toggleStepCopy(int index) {
        List<DslScene.DslStep> steps = getSteps();
        if (index < 0 || index >= steps.size()) {
            return;
        }
        DslScene.DslStep step = steps.get(index);
        if (copiedSteps.containsKey(step)) {
            copiedSteps.remove(step);
        } else {
            copiedSteps.put(step, deepCopy(step));
        }
        updateClipboardFromSelection();
        rebuildListPreservingScroll();
    }

    private void updateClipboardFromSelection() {
        // The list order, rather than the order of right-clicks, determines paste order.
        clipboard = getSteps().stream()
            .filter(copiedSteps::containsKey)
            .map(copiedSteps::get)
            .toList();
    }

    private void openInsertAfter(int afterIndex) {
        rememberCurrentListScroll();
        ScreenOpener.open(new StepTypeSelectorScreen(scene, sceneIndex, this, 0, afterIndex));
    }

    private void pasteAfter(int afterIndex) {
        if (!copiedSteps.isEmpty()) {
            updateClipboardFromSelection();
        }
        if (clipboard.isEmpty()) {
            return;
        }
        insertStepsAndSave(afterIndex, clipboard.stream().map(SceneEditorScreen::deepCopy).toList());
        rebuildListPreservingScroll();
    }

    private void performUndo() {
        List<DslScene.DslStep> restored = undoManager().undo(getSteps());
        if (restored == null) {
            return;
        }
        setMutableSteps(restored);
        saveToFile();
        rebuildListPreservingScroll();
    }

    private void performRedo() {
        List<DslScene.DslStep> restored = undoManager().redo(getSteps());
        if (restored == null) {
            return;
        }
        setMutableSteps(restored);
        saveToFile();
        rebuildListPreservingScroll();
    }

    private void setMutableSteps(List<DslScene.DslStep> newSteps) {
        copiedSteps.clear();
        session.replaceSteps(newSteps);
    }

    private List<DslScene.DslStep> getSteps() { return session.steps(); }

    private List<DslScene.DslStep> getMutableSteps() { return session.mutableSteps(); }

    private List<DslScene.SceneSegment> getScenes() {
        if (scene.scenes != null && !scene.scenes.isEmpty()) {
            return scene.scenes;
        }
        List<DslScene.SceneSegment> result = new ArrayList<>();
        DslScene.SceneSegment fallback = new DslScene.SceneSegment();
        fallback.steps = new ArrayList<>();
        result.add(fallback);
        return result;
    }

    private String screenTitleText() {
        String sceneTitle = scene.title != null && !scene.title.isEmpty() ? scene.title.resolve() : scene.id;
        return UIText.of("ponderer.ui.scene_editor.title", sceneTitle);
    }

    private String screenSubtitleText() {
        int sceneCount = getSceneCount();
        String currentSceneName = currentSceneName();
        if (sceneCount > 1) {
            return "[" + (sceneIndex + 1) + "/" + sceneCount + ": " + currentSceneName + "]";
        }
        return currentSceneName;
    }

    private String currentSceneName() {
        int sceneCount = getSceneCount();
        if (sceneIndex >= 0 && sceneIndex < sceneCount && scene.scenes != null && sceneIndex < scene.scenes.size()) {
            DslScene.SceneSegment currentScene = scene.scenes.get(sceneIndex);
            if (currentScene.title != null && !currentScene.title.isEmpty()) {
                return currentScene.title.resolve();
            }
            if (currentScene.id != null && !currentScene.id.isBlank()) {
                return currentScene.id;
            }
        }
        return scene.id != null ? scene.id : String.valueOf(sceneIndex + 1);
    }

    public void addStepAndSave(DslScene.DslStep newStep) {
        insertStepAndSave(-1, newStep);
    }

    public void insertStepAndSave(int afterIndex, DslScene.DslStep newStep) {
        insertStepsAndSave(afterIndex, List.of(newStep));
    }

    private void insertStepsAndSave(int afterIndex, List<DslScene.DslStep> newSteps) {
        session.execute(steps -> {
            int insertedIndex = afterIndex >= 0 && afterIndex < steps.size() ? afterIndex + 1 : steps.size();
            for (DslScene.DslStep newStep : newSteps) {
                steps.add(insertedIndex, newStep);
                if (isShowInterfaceStep(newStep)) {
                    pruneFollowingInterfaceSlotSteps(steps, insertedIndex);
                }
                insertedIndex++;
            }
        }, undoManager());
        saveToFile();
    }

    public void replaceStepAndSave(int index, DslScene.DslStep newStep) {
        List<DslScene.DslStep> steps = getMutableSteps();
        if (index >= 0 && index < steps.size()) {
            session.execute(mutable -> {
                DslScene.DslStep oldStep = mutable.get(index);
                mutable.set(index, newStep);
                if (didShowInterfaceContextChange(oldStep, newStep)) {
                    pruneFollowingInterfaceSlotSteps(mutable, index);
                }
            }, undoManager());
            saveToFile();
        }
    }

    private static boolean isShowInterfaceStep(@Nullable DslScene.DslStep step) {
        return step != null && step.type != null && "show_interface".equalsIgnoreCase(step.type);
    }

    private static boolean didShowInterfaceContextChange(@Nullable DslScene.DslStep oldStep,
                                                         @Nullable DslScene.DslStep newStep) {
        if (!isShowInterfaceStep(newStep)) {
            return false;
        }
        if (!isShowInterfaceStep(oldStep)) {
            return true;
        }
        return !Objects.equals(oldStep.block, newStep.block)
            || !Objects.equals(oldStep.blockProperties, newStep.blockProperties);
    }

    private static void pruneFollowingInterfaceSlotSteps(List<DslScene.DslStep> steps, int showInterfaceIndex) {
        for (int i = showInterfaceIndex + 1; i < steps.size();) {
            DslScene.DslStep step = steps.get(i);
            if (isShowInterfaceStep(step)) {
                break;
            }
            if (step != null && step.type != null && "change_interface_slot".equalsIgnoreCase(step.type)) {
                steps.remove(i);
                continue;
            }
            i++;
        }
    }

    public void removeStepAndSave(int index) {
        List<DslScene.DslStep> steps = getMutableSteps();
        if (index >= 0 && index < steps.size()) {
            session.execute(mutable -> {
                mutable.remove(index);
            }, undoManager());
            saveToFile();
            rebuildListPreservingScroll();
        }
    }

    private void moveStepUp(int index) {
        List<DslScene.DslStep> steps = getMutableSteps();
        if (index > 0 && index < steps.size()) {
            session.execute(mutable -> {
                DslScene.DslStep temp = mutable.get(index);
                mutable.set(index, mutable.get(index - 1));
                mutable.set(index - 1, temp);
            }, undoManager());
            saveToFile();
            rebuildListPreservingScroll();
        }
    }

    private void moveStepDown(int index) {
        List<DslScene.DslStep> steps = getMutableSteps();
        if (index >= 0 && index < steps.size() - 1) {
            session.execute(mutable -> {
                DslScene.DslStep temp = mutable.get(index);
                mutable.set(index, mutable.get(index + 1));
                mutable.set(index + 1, temp);
            }, undoManager());
            saveToFile();
            rebuildListPreservingScroll();
        }
    }

    private void openSplitTypeSelector() {
        rememberCurrentListScroll();
        ScreenOpener.open(new SceneTypeSelectorScreen(this));
    }

    public void insertSplitStep(String startStepType) {
        String normalizedType = startStepType != null && !startStepType.isBlank()
            ? startStepType.toLowerCase(Locale.ROOT)
            : "show_structure";
        if (!"show_structure".equals(normalizedType) && !"show_interface".equals(normalizedType)) {
            normalizedType = "show_structure";
        }

        undoManager().saveState(getSteps());
        int newSceneIndex = -1;

        boolean firstSegment = scene.scenes == null || scene.scenes.isEmpty();
        if (firstSegment || (sceneIndex >= 0 && sceneIndex < scene.scenes.size())) {
            DslScene.SceneSegment newScene = new DslScene.SceneSegment();
            newScene.steps = new ArrayList<>();
            DslScene.DslStep startStep = new DslScene.DslStep();
            startStep.type = normalizedType;
            newScene.steps.add(startStep);
            DslScene.DslStep idleStep = new DslScene.DslStep();
            idleStep.type = "idle";
            idleStep.duration = 20;
            newScene.steps.add(idleStep);
            newScene.id = firstSegment ? "scene_1" : generateUniqueSegmentId(scene);
            if (!(scene.scenes instanceof ArrayList)) {
                scene.scenes = scene.scenes == null ? new ArrayList<>() : new ArrayList<>(scene.scenes);
            }
            newSceneIndex = firstSegment ? 0 : sceneIndex + 1;
            scene.scenes.add(newSceneIndex, newScene);
        } else {
            DslScene.DslStep ns = new DslScene.DslStep();
            ns.type = "next_scene";
            getMutableSteps().add(ns);
            DslScene.DslStep startStep = new DslScene.DslStep();
            startStep.type = normalizedType;
            getMutableSteps().add(startStep);
            DslScene.DslStep idleStep = new DslScene.DslStep();
            idleStep.type = "idle";
            idleStep.duration = 20;
            getMutableSteps().add(idleStep);
        }
        saveToFile();

        if (newSceneIndex >= 0) {
            sceneIndex = newSceneIndex;
            session.selectSegment(sceneIndex);
            rememberListScroll(0);
            clearCurrentPonderHistory();
            ScreenOpener.open(new SceneDescEditorScreen(scene, sceneIndex, this, true));
            return;
        }

        rebuildListAtTop();
    }

    private static String generateUniqueSegmentId(DslScene scene) {
        Set<String> existing = new HashSet<>();
        if (scene.scenes != null) {
            for (DslScene.SceneSegment segment : scene.scenes) {
                if (segment.id != null) {
                    existing.add(segment.id);
                }
            }
        }
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        String candidate;
        do {
            candidate = "s_" + Integer.toHexString(rng.nextInt(0x10000, 0xFFFFF));
        } while (existing.contains(candidate));
        return candidate;
    }

    private boolean saveToFile() {
        var outcome = new com.nododiiiii.ponderer.application.SceneApplicationService(
            com.nododiiiii.ponderer.application.Workspaces.files().scenes()).save(scene);
        var result = outcome.persistence();
        if (result.isSuccess()) {
            clearStatusMessages();
            preview.load(scene, sceneIndex);
            Minecraft.getInstance().execute(com.nododiiiii.ponderer.application.SceneRefreshCoordinator::reloadPonderIndex);
            return true;
        }
        setErrorMessage(UIText.saveError(result));
        return false;
    }

    private void confirmDeleteScene() {
        int sceneCount = getSceneCount();

        if (sceneCount > 1 && scene.scenes != null && !scene.scenes.isEmpty()) {
            new ConfirmationScreen()
                .centered()
                .withText(net.minecraft.network.chat.Component.translatable("ponderer.ui.scene_editor.delete_scene_title"))
                .addText(net.minecraft.network.chat.Component.translatable(
                    "ponderer.ui.scene_editor.delete_scene_msg",
                    currentSceneName()))
                .withAction(confirmed -> {
                    if (confirmed) {
                        deleteCurrentScene();
                    }
                })
                .open(this);
            return;
        }

        new ConfirmationScreen()
            .centered()
            .withText(net.minecraft.network.chat.Component.translatable("ponderer.ui.scene_editor.delete_ponder_title"))
            .addText(net.minecraft.network.chat.Component.translatable(
                "ponderer.ui.scene_editor.delete_ponder_msg",
                scene.id))
            .withAction(confirmed -> {
                if (confirmed) {
                    deletePonderAndExit();
                }
            })
            .open(this);
    }

    private void deleteCurrentScene() {
        if (scene.scenes != null && scene.scenes.size() > 1
            && sceneIndex >= 0 && sceneIndex < scene.scenes.size()) {
            if (!(scene.scenes instanceof ArrayList)) {
                scene.scenes = new ArrayList<>(scene.scenes);
            }
            scene.scenes.remove(sceneIndex);
            if (sceneIndex >= scene.scenes.size()) {
                sceneIndex = scene.scenes.size() - 1;
                session.selectSegment(sceneIndex);
            }
            clearCurrentPonderHistory();
            rememberListScroll(0);
            saveToFile();
            Minecraft.getInstance().setScreen(this);
        }
    }

    private void deletePonderAndExit() {
        clearCurrentPonderHistory();
        boolean deleted = com.nododiiiii.ponderer.ponder.SceneStore.deleteSceneLocal(scene.id);
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(null);
        if (deleted) {
            com.nododiiiii.ponderer.ponder.SceneStore.reloadFromDisk();
            mc.execute(com.nododiiiii.ponderer.application.SceneRefreshCoordinator::reloadPonderIndex);
        }
    }

    private boolean cancelAndExit() {
        reopenPonderUi(false);
        return true;
    }

    private void reloadAndExit() {
        reopenPonderUi(true);
    }

    private void reopenPonderUi(boolean reloadFromDisk) {
        Minecraft mc = Minecraft.getInstance();
        PonderScreenNavigation.restoreReturnState(ponderReturnState);
        mc.setScreen(null);

        final net.minecraft.resources.ResourceLocation reopenId =
            com.nododiiiii.ponderer.runtime.core.SceneItemBindings.firstItem(scene);
        final String targetSceneId = computeCurrentSceneId();
        final String nbtFilter = com.nododiiiii.ponderer.runtime.core.SceneItemBindings.firstNbtFilter(scene);

        if (reloadFromDisk) {
            com.nododiiiii.ponderer.ponder.SceneStore.reloadFromDisk();
        }

        mc.execute(() -> {
            if (reloadFromDisk) {
                com.nododiiiii.ponderer.application.SceneRefreshCoordinator.reloadPonderIndex();
            }
            if (reopenId != null && net.createmod.ponder.foundation.PonderIndex.getSceneAccess().doScenesExistForId(reopenId)) {
                net.createmod.ponder.foundation.ui.PonderUI ui;
                if (nbtFilter != null && !nbtFilter.isBlank()) {
                    ui = net.createmod.ponder.foundation.ui.PonderUI.of(buildFilteredStack(reopenId, nbtFilter));
                } else {
                    ui = net.createmod.ponder.foundation.ui.PonderUI.of(reopenId);
                }
                if (targetSceneId != null) {
                    navigateToScene(ui, targetSceneId);
                }
                mc.setScreen(ui);
            }
        });
    }

    private static net.minecraft.world.item.ItemStack buildFilteredStack(net.minecraft.resources.ResourceLocation itemId,
                                                                         String nbtFilter) {
        return com.nododiiiii.ponderer.runtime.core.NbtSceneFilter.createStack(itemId, nbtFilter);
    }

    @Nullable
    private String computeCurrentSceneId() {
        net.minecraft.resources.ResourceLocation baseId = net.minecraft.resources.ResourceLocation.tryParse(scene.id);
        if (baseId == null) {
            return null;
        }
        String basePath = baseId.getPath();
        List<DslScene.SceneSegment> sceneList = getScenes();
        if (sceneList.size() <= 1) {
            return "ponderer:" + basePath;
        }
        if (sceneIndex < 0 || sceneIndex >= sceneList.size()) {
            return "ponderer:" + basePath;
        }
        DslScene.SceneSegment current = sceneList.get(sceneIndex);
        String suffix = current.id != null && !current.id.isBlank() ? current.id : String.valueOf(sceneIndex + 1);
        return "ponderer:" + basePath + "_" + suffix;
    }

    private static void navigateToScene(net.createmod.ponder.foundation.ui.PonderUI ui, String targetSceneId) {
        com.nododiiiii.ponderer.runtime.mixin.PonderUIAccessor accessor = (com.nododiiiii.ponderer.runtime.mixin.PonderUIAccessor) (Object) ui;
        List<net.createmod.ponder.foundation.PonderScene> scenes = accessor.ponderer$getScenes();
        for (int i = 0; i < scenes.size(); i++) {
            if (targetSceneId.equals(scenes.get(i).getId().toString())) {
                accessor.ponderer$setIndex(i);
                accessor.ponderer$getLazyIndex().chase(i, 1.0f / 3, net.createmod.catnip.animation.LerpedFloat.Chaser.EXP);
                accessor.ponderer$getLazyIndex().startWithValue(i);
                break;
            }
        }
    }


}
