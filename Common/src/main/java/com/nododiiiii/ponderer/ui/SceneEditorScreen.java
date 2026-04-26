package com.nododiiiii.ponderer.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.LocalizedText;
import com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeListScreen;
import com.nododiiiii.ponderer.ui.catnip.ActionStripListEntry;
import com.nododiiiii.ponderer.ui.catnip.PonderIconStencils;
import com.nododiiiii.ponderer.ui.catnip.SceneStepListEntry;
import com.nododiiiii.ponderer.ui.catnip.SectionHeaderListEntry;
import com.nododiiiii.ponderer.ui.catnip.WorkspaceHeaderListEntry;
import net.createmod.catnip.gui.ConfirmationScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.catnip.lang.FontHelper;
import net.createmod.catnip.lang.FontHelper.Palette;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SceneEditorScreen extends AbstractDeclarativeListScreen {

    private static final Gson STEP_GSON = new GsonBuilder()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter())
        .create();
    private static final int SIDEBAR_BUTTON_SIZE = 20;
    private static final int SIDEBAR_BUTTON_GAP = 10;
    private static final int STEP_ACTION_ICON_SIZE = 12;
    private static final HistorySession HISTORY_SESSION = new HistorySession();

    @Nullable
    private static DslScene.DslStep clipboard = null;

    private final DslScene scene;
    private int sceneIndex;
    private double pendingListScroll = 0;

    @Nullable
    private BoxWidget undoButton;
    @Nullable
    private BoxWidget redoButton;

    public SceneEditorScreen(DslScene scene, int sceneIndex) {
        super(null, "ponderer.ui.scope.editor", "ponderer.ui.scene_editor", 400);
        this.scene = scene;
        this.sceneIndex = sceneIndex;
    }

    public static boolean canModifyScene(DslScene scene) {
        return scene != null && (scene.isEditable(Config.DEFAULT_EDITABLE.get()) || Config.DEVELOPER_MODE.get());
    }

    @Override
    protected void init() {
        super.init();
        configureActionButtons();
        initHistoryButtons();
        if (list != null) {
            list.setScrollAmount(pendingListScroll);
        }
    }

    @Override
    public void tick() {
        super.tick();
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
        if (steps.isEmpty()) {
            entries.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.scene_editor.no_steps")));
            return;
        }

        for (int i = 0; i < steps.size(); i++) {
            int stepIndex = i;
            entries.add(new SceneStepListEntry(
                () -> (stepIndex + 1) + ". " + formatStep(steps.get(stepIndex)),
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
                    SceneStepListEntry.button(
                        "+",
                        () -> List.of(net.minecraft.network.chat.Component.literal(UIText.of("ponderer.ui.scene_editor.btn.insert"))),
                        () -> openInsertAfter(stepIndex),
                        null,
                        () -> 0xFF80FF80,
                        () -> !isActionDisabled(2, stepIndex)),
                    SceneStepListEntry.button(
                        "C",
                        this::copyButtonTooltip,
                        () -> copyStep(stepIndex),
                        null,
                        () -> 0xFF80C0FF,
                        () -> true),
                    SceneStepListEntry.button(
                        "P",
                        this::pasteButtonTooltip,
                        () -> pasteAfter(stepIndex),
                        null,
                        () -> 0xFFFFD080,
                        () -> clipboard != null),
                    SceneStepListEntry.dangerIconButton(
                        PonderIconStencils.centered(PonderGuiTextures.ICON_DISABLE, STEP_ACTION_ICON_SIZE),
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
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void configureActionButtons() {
        if (saveChanges != null) {
            saveChanges.withCallback(this::saveEdits);
            saveChanges.getToolTip().clear();
            saveChanges.getToolTip().add(net.minecraft.network.chat.Component.translatable("ponderer.ui.save"));
        }
        if (discardChanges != null) {
            discardChanges.withCallback(this::cancelAndExit);
            discardChanges.getToolTip().clear();
            discardChanges.getToolTip().add(net.minecraft.network.chat.Component.translatable("ponderer.ui.cancel"));
        }
        if (goBack != null) {
            goBack.withCallback(this::reloadAndExit);
            goBack.visible = true;
            goBack.active = true;
            goBack.getToolTip().clear();
            goBack.getToolTip().add(net.minecraft.network.chat.Component.translatable("ponderer.ui.scene_editor.back"));
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
        return width / 2 + currentListWidthValue() / 2 + 10;
    }

    private BoxWidget createHistoryButton(int x, int y, Runnable callback,
                                          net.createmod.catnip.gui.element.DelegatedStencilElement icon,
                                          String tooltipTitleKey, String tooltipBodyKey) {
        BoxWidget button = new BoxWidget(x, y, SIDEBAR_BUTTON_SIZE, SIDEBAR_BUTTON_SIZE)
            .withPadding(2, 2)
            .withCallback(callback);
        PonderIconStencils.attach(button, icon);
        configureSidebarTooltip(
            button,
            Component.translatable(tooltipTitleKey),
            Component.translatable(tooltipBodyKey));
        button.updateGradientFromState();
        addRenderableWidget(button);
        return button;
    }

    private void configureSidebarTooltip(BoxWidget button, Component title, Component detail) {
        button.getToolTip().clear();
        button.getToolTip().add(title);
        button.getToolTip().addAll(FontHelper.cutTextComponent(detail, Palette.ALL_GRAY));
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

    private void rememberScrollPosition() {
        pendingListScroll = currentListScroll();
    }

    private void rebuildEntriesPreservingScroll() {
        double scroll = currentListScroll();
        pendingListScroll = scroll;
        rebuildEntries(scroll);
    }

    private void rebuildEntriesAtTop() {
        pendingListScroll = 0;
        rebuildEntries(0.0);
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
        pendingListScroll = 0;
        clearStatusMessages();
        rebuildEntriesAtTop();
    }

    private void openSceneDescription() {
        rememberScrollPosition();
        ScreenOpener.open(new SceneDescEditorScreen(scene, sceneIndex, this));
    }

    private void openTriggerEditor() {
        rememberScrollPosition();
        ScreenOpener.open(new TriggerEditorScreen(scene, sceneIndex, this));
    }

    private void openEditorForStep(int index) {
        List<DslScene.DslStep> steps = getSteps();
        if (index < 0 || index >= steps.size()) {
            return;
        }
        rememberScrollPosition();
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
        return List.of(net.minecraft.network.chat.Component.literal(UIText.of("ponderer.ui.scene_editor.btn.copy")));
    }

    private List<net.minecraft.network.chat.Component> pasteButtonTooltip() {
        return List.of(net.minecraft.network.chat.Component.literal(UIText.of("ponderer.ui.scene_editor.btn.paste_after")));
    }

    private void copyStep(int index) {
        List<DslScene.DslStep> steps = getSteps();
        if (index >= 0 && index < steps.size()) {
            clipboard = deepCopy(steps.get(index));
            rebuildEntriesPreservingScroll();
        }
    }

    private void openInsertAfter(int afterIndex) {
        rememberScrollPosition();
        ScreenOpener.open(new StepTypeSelectorScreen(scene, sceneIndex, this, 0, afterIndex));
    }

    private void pasteAfter(int afterIndex) {
        if (clipboard == null) {
            return;
        }
        insertStepAndSave(afterIndex, deepCopy(clipboard));
        rebuildEntriesPreservingScroll();
    }

    private void performUndo() {
        List<DslScene.DslStep> restored = undoManager().undo(getSteps());
        if (restored == null) {
            return;
        }
        setMutableSteps(restored);
        saveToFile();
        rebuildEntriesPreservingScroll();
    }

    private void performRedo() {
        List<DslScene.DslStep> restored = undoManager().redo(getSteps());
        if (restored == null) {
            return;
        }
        setMutableSteps(restored);
        saveToFile();
        rebuildEntriesPreservingScroll();
    }

    private void setMutableSteps(List<DslScene.DslStep> newSteps) {
        if (scene.scenes != null && !scene.scenes.isEmpty()
            && sceneIndex >= 0 && sceneIndex < scene.scenes.size()) {
            scene.scenes.get(sceneIndex).steps = new ArrayList<>(newSteps);
        }
    }

    private List<DslScene.DslStep> getSteps() {
        if (scene.scenes != null && !scene.scenes.isEmpty()
            && sceneIndex >= 0 && sceneIndex < scene.scenes.size()) {
            List<DslScene.DslStep> steps = scene.scenes.get(sceneIndex).steps;
            return steps != null ? steps : List.of();
        }
        return List.of();
    }

    private List<DslScene.DslStep> getMutableSteps() {
        if (scene.scenes != null && !scene.scenes.isEmpty()
            && sceneIndex >= 0 && sceneIndex < scene.scenes.size()) {
            DslScene.SceneSegment sc = scene.scenes.get(sceneIndex);
            if (sc.steps == null) {
                sc.steps = new ArrayList<>();
            } else if (!(sc.steps instanceof ArrayList)) {
                sc.steps = new ArrayList<>(sc.steps);
            }
            return sc.steps;
        }
        return new ArrayList<>();
    }

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

    private String formatStep(DslScene.DslStep step) {
        if (step == null || step.type == null) {
            return UIText.of("ponderer.ui.invalid");
        }
        return switch (step.type.toLowerCase(Locale.ROOT)) {
            case "show_structure" ->
                UIText.of("ponderer.ui.step.summary.show_structure", stepTypeName("show_structure"));
            case "idle" -> UIText.of("ponderer.ui.step.summary.idle", stepTypeName("idle"), step.durationOrDefault(20),
                UIText.of("ponderer.ui.ticks"));
            case "text" -> UIText.of("ponderer.ui.step.summary.text", stepTypeName("text"),
                truncate(step.text != null ? step.text.resolve() : "", 20));
            case "shared_text" -> UIText.of("ponderer.ui.step.summary.shared_text", stepTypeName("shared_text"),
                step.key != null ? step.key : "?");
            case "create_entity" -> UIText.of("ponderer.ui.step.summary.single_arg", stepTypeName("create_entity"),
                step.entity != null ? step.entity : "?");
            case "create_item_entity" -> UIText.of("ponderer.ui.step.summary.single_arg",
                stepTypeName("create_item_entity"), step.item != null ? step.item : "?");
            case "rotate_camera_y" -> UIText.of("ponderer.ui.step.summary.rotate_camera",
                stepTypeName("rotate_camera_y"), step.degrees != null ? step.degrees : 90);
            case "zoom_scene" -> stepTypeName("zoom_scene");
            case "highlight_section" -> stepTypeName("highlight_section");
            case "show_controls" -> UIText.of(
                "ponderer.ui.step.summary.show_controls_action_item",
                stepTypeName("show_controls"),
                controlActionName(step.action),
                step.item != null && !step.item.isBlank() ? step.item : UIText.of("ponderer.ui.none"));
            case "show_interface" -> UIText.of("ponderer.ui.step.summary.single_arg",
                stepTypeName("show_interface"), step.block != null ? step.block : "?");
            case "change_interface_slot" -> UIText.of("ponderer.ui.step.summary.slot_count",
                stepTypeName("change_interface_slot"),
                step.interfaceSlots != null ? step.interfaceSlots.size() : 0);
            case "click_interface" -> stepTypeName("click_interface");
            case "encapsulate_bounds" -> stepTypeName("encapsulate_bounds");
            case "play_sound" -> UIText.of("ponderer.ui.step.summary.single_arg", stepTypeName("play_sound"),
                step.sound != null ? step.sound : "?");
            case "set_block" -> UIText.of("ponderer.ui.step.summary.single_arg", stepTypeName("set_block"),
                step.block != null ? step.block : "?");
            case "destroy_block" -> stepTypeName("destroy_block");
            case "replace_blocks" -> UIText.of("ponderer.ui.step.summary.single_arg", stepTypeName("replace_blocks"),
                step.block != null ? step.block : "?");
            case "hide_section" -> stepTypeName("hide_section");
            case "show_section_and_merge" -> stepTypeName("show_section_and_merge");
            case "toggle_redstone_power" -> stepTypeName("toggle_redstone_power");
            case "modify_block_entity_nbt" -> stepTypeName("modify_block_entity_nbt");
            case "rotate_section" -> stepTypeName("rotate_section");
            case "move_section" -> stepTypeName("move_section");
            case "indicate_redstone" -> stepTypeName("indicate_redstone");
            case "indicate_success" -> stepTypeName("indicate_success");
            case "clear_entities" -> UIText.of("ponderer.ui.step.summary.single_arg",
                stepTypeName("clear_entities"), step.entity != null && !step.entity.isEmpty() ? step.entity : "*");
            case "clear_item_entities" -> UIText.of("ponderer.ui.step.summary.single_arg",
                stepTypeName("clear_item_entities"), step.item != null && !step.item.isEmpty() ? step.item : "*");
            case "modify_entities_nbt" -> UIText.of("ponderer.ui.step.summary.single_arg",
                stepTypeName("modify_entities_nbt"), step.entity != null && !step.entity.isEmpty() ? step.entity : "*");
            case "modify_item_entities_nbt" -> UIText.of("ponderer.ui.step.summary.single_arg",
                stepTypeName("modify_item_entities_nbt"), step.item != null && !step.item.isEmpty() ? step.item : "*");
            case "next_scene" -> UIText.of("ponderer.ui.step.summary.next_scene");
            default -> step.type;
        };
    }

    private String stepTypeName(String type) {
        return UIText.of("ponderer.ui.step.type." + type);
    }

    private String controlActionName(String action) {
        if (action == null || action.isBlank()) {
            return UIText.of("ponderer.ui.none");
        }
        String key = "ponderer.ui.show_controls.action." + action.toLowerCase(Locale.ROOT);
        String translated = UIText.of(key);
        return key.equals(translated) ? action : translated;
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }

    public void addStepAndSave(DslScene.DslStep newStep) {
        insertStepAndSave(-1, newStep);
    }

    public void insertStepAndSave(int afterIndex, DslScene.DslStep newStep) {
        undoManager().saveState(getSteps());
        List<DslScene.DslStep> steps = getMutableSteps();
        int insertedIndex;
        if (afterIndex >= 0 && afterIndex < steps.size()) {
            steps.add(afterIndex + 1, newStep);
            insertedIndex = afterIndex + 1;
        } else {
            steps.add(newStep);
            insertedIndex = steps.size() - 1;
        }
        if (isShowInterfaceStep(newStep)) {
            pruneFollowingInterfaceSlotSteps(steps, insertedIndex);
        }
        saveToFile();
    }

    public void replaceStepAndSave(int index, DslScene.DslStep newStep) {
        List<DslScene.DslStep> steps = getMutableSteps();
        if (index >= 0 && index < steps.size()) {
            undoManager().saveState(getSteps());
            DslScene.DslStep oldStep = steps.get(index);
            steps.set(index, newStep);
            if (didShowInterfaceContextChange(oldStep, newStep)) {
                pruneFollowingInterfaceSlotSteps(steps, index);
            }
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
            undoManager().saveState(getSteps());
            steps.remove(index);
            saveToFile();
            rebuildEntriesPreservingScroll();
        }
    }

    private void moveStepUp(int index) {
        List<DslScene.DslStep> steps = getMutableSteps();
        if (index > 0 && index < steps.size()) {
            undoManager().saveState(getSteps());
            DslScene.DslStep temp = steps.get(index);
            steps.set(index, steps.get(index - 1));
            steps.set(index - 1, temp);
            saveToFile();
            rebuildEntriesPreservingScroll();
        }
    }

    private void moveStepDown(int index) {
        List<DslScene.DslStep> steps = getMutableSteps();
        if (index >= 0 && index < steps.size() - 1) {
            undoManager().saveState(getSteps());
            DslScene.DslStep temp = steps.get(index);
            steps.set(index, steps.get(index + 1));
            steps.set(index + 1, temp);
            saveToFile();
            rebuildEntriesPreservingScroll();
        }
    }

    private void openSplitTypeSelector() {
        rememberScrollPosition();
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

        if (scene.scenes != null && !scene.scenes.isEmpty()
            && sceneIndex >= 0 && sceneIndex < scene.scenes.size()) {
            DslScene.SceneSegment newScene = new DslScene.SceneSegment();
            newScene.steps = new ArrayList<>();
            DslScene.DslStep startStep = new DslScene.DslStep();
            startStep.type = normalizedType;
            newScene.steps.add(startStep);
            DslScene.DslStep idleStep = new DslScene.DslStep();
            idleStep.type = "idle";
            idleStep.duration = 20;
            newScene.steps.add(idleStep);
            newScene.id = generateUniqueSegmentId(scene);
            if (!(scene.scenes instanceof ArrayList)) {
                scene.scenes = new ArrayList<>(scene.scenes);
            }
            scene.scenes.add(sceneIndex + 1, newScene);
            newSceneIndex = sceneIndex + 1;
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
            pendingListScroll = 0;
            clearCurrentPonderHistory();
            ScreenOpener.open(new SceneDescEditorScreen(scene, sceneIndex, this));
            return;
        }

        rebuildEntriesAtTop();
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
        com.nododiiiii.ponderer.ponder.SceneStore.LocalSaveResult result = com.nododiiiii.ponderer.ponder.SceneStore.saveSceneToLocalDetailed(scene);
        if (result.isSuccess()) {
            clearStatusMessages();
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
            }
            clearCurrentPonderHistory();
            pendingListScroll = 0;
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
            mc.execute(net.createmod.ponder.foundation.PonderIndex::reload);
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
        net.createmod.catnip.gui.ScreenOpener.clearStack();
        PonderItemGridScreen.returnScreen = null;
        mc.setScreen(null);

        net.minecraft.resources.ResourceLocation itemId = null;
        if (scene.items != null && !scene.items.isEmpty()) {
            itemId = net.minecraft.resources.ResourceLocation.tryParse(scene.items.get(0));
        }
        final net.minecraft.resources.ResourceLocation reopenId = itemId;
        final String targetSceneId = computeCurrentSceneId();
        final String nbtFilter = scene.nbtFilter;

        if (reloadFromDisk) {
            com.nododiiiii.ponderer.ponder.SceneStore.reloadFromDisk();
        }

        mc.execute(() -> {
            if (reloadFromDisk) {
                net.createmod.ponder.foundation.PonderIndex.reload();
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
        net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId);
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
        try {
            net.minecraft.nbt.CompoundTag filterTag = net.minecraft.nbt.TagParser.parseTag(nbtFilter);
            net.minecraft.nbt.CompoundTag fullTag = new net.minecraft.nbt.CompoundTag();
            fullTag.putString("id", itemId.toString());
            fullTag.putByte("Count", (byte) 1);
            fullTag.put("tag", filterTag);
            net.minecraft.world.item.ItemStack parsed = net.minecraft.world.item.ItemStack.of(fullTag);
            if (!parsed.isEmpty()) {
                stack = parsed;
            }
        } catch (Exception ignored) {
        }
        return stack;
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
        com.nododiiiii.ponderer.mixin.PonderUIAccessor accessor = (com.nododiiiii.ponderer.mixin.PonderUIAccessor) (Object) ui;
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

    private static final class HistorySession {
        @Nullable
        private String activePonderKey;
        private final Map<String, UndoManager> sceneHistories = new HashMap<>();
        private boolean preserveOnNextPonderUiRemoval;

        private UndoManager historyFor(String ponderKey, String sceneKey) {
            switchToPonder(ponderKey);
            return sceneHistories.computeIfAbsent(sceneKey, ignored -> new UndoManager());
        }

        private void markUiToEditorTransition(String ponderKey) {
            switchToPonder(ponderKey);
            preserveOnNextPonderUiRemoval = true;
        }

        private void handlePonderUiRemoved(String ponderKey) {
            if (!Objects.equals(activePonderKey, ponderKey)) {
                return;
            }
            if (preserveOnNextPonderUiRemoval) {
                preserveOnNextPonderUiRemoval = false;
                return;
            }
            clear();
        }

        private void clearIfLeavingPonder(String ponderKey) {
            if (activePonderKey != null && !Objects.equals(activePonderKey, ponderKey)) {
                clear();
            }
        }

        private void clearPonder(String ponderKey) {
            if (Objects.equals(activePonderKey, ponderKey)) {
                sceneHistories.clear();
                preserveOnNextPonderUiRemoval = false;
            }
        }

        private void switchToPonder(String ponderKey) {
            if (Objects.equals(activePonderKey, ponderKey)) {
                return;
            }
            activePonderKey = ponderKey;
            sceneHistories.clear();
            preserveOnNextPonderUiRemoval = false;
        }

        private void clear() {
            activePonderKey = null;
            sceneHistories.clear();
            preserveOnNextPonderUiRemoval = false;
        }
    }
}
