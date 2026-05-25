package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ui.catnip.AbstractReadonlyDeclarativeListScreen;
import com.nododiiiii.ponderer.ui.catnip.FullButtonListEntry;
import com.nododiiiii.ponderer.ui.catnip.PageTurnListEntry;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.gui.ScreenOpener;

import java.util.List;
import java.util.Locale;

public class StepTypeSelectorScreen extends AbstractReadonlyDeclarativeListScreen {

    private static final int STEP_TYPE_BUTTON_W = 180;

    private static final String[][] PAGE_TYPES = {
        {"idle", "text", "show_controls", "rotate_camera_y", "zoom_scene"},
        {"set_block", "destroy_block", "replace_blocks", "modify_block_entity_nbt", "show_extra_structure"},
        {"show_section_and_merge", "hide_section", "rotate_section", "move_section"},
        {"create_entity", "create_item_entity", "clear_entities", "clear_item_entities", "modify_entities_nbt",
            "modify_item_entities_nbt"},
        {"highlight_section", "indicate_success", "indicate_redstone", "toggle_redstone_power", "play_sound"}
    };
    private static final String[] PAGE_KEYS = {
        "ponderer.ui.step.page.story",
        "ponderer.ui.step.page.block",
        "ponderer.ui.step.page.section",
        "ponderer.ui.step.page.entity",
        "ponderer.ui.step.page.effect"
    };
    private static final String[][] INTERFACE_SCENE_PAGE_TYPES = {
        {"idle", "text", "show_controls", "change_interface_slot", "click_interface", "play_sound"}
    };
    private static final String[] INTERFACE_SCENE_PAGE_KEYS = {"ponderer.ui.step.page.story"};

    private final DslScene scene;
    private final int sceneIndex;
    private final SceneEditorScreen parent;
    private final String[][] pageTypes;
    private final String[] pageKeys;
    private final int pageIndex;
    private final int insertAfterIndex;

    public StepTypeSelectorScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        this(scene, sceneIndex, parent, 0, -1);
    }

    public StepTypeSelectorScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent, int pageIndex) {
        this(scene, sceneIndex, parent, pageIndex, -1);
    }

    public StepTypeSelectorScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent, int pageIndex,
                                  int insertAfterIndex) {
        super(parent, "ponderer.ui.scope.editor", "ponderer.ui.step_selector.title", UILayoutConstants.EDITOR_LIST_W);
        this.scene = scene;
        this.sceneIndex = sceneIndex;
        this.parent = parent;
        if (isInterfaceStartScene(scene, sceneIndex)) {
            this.pageTypes = INTERFACE_SCENE_PAGE_TYPES;
            this.pageKeys = INTERFACE_SCENE_PAGE_KEYS;
        } else {
            this.pageTypes = PAGE_TYPES;
            this.pageKeys = PAGE_KEYS;
        }
        this.pageIndex = Math.max(0, Math.min(pageIndex, this.pageTypes.length - 1));
        this.insertAfterIndex = insertAfterIndex;
    }

    @Override
    protected void collectEntries(List<ConfigScreenList.Entry> entries) {
        entries.add(new PageTurnListEntry(
            this::currentPageLabel,
            () -> openPage(pageIndex - 1),
            () -> openPage(pageIndex + 1),
            this::previousPageTooltip,
            this::nextPageTooltip,
            () -> pageIndex > 0,
            () -> pageIndex + 1 < pageTypes.length));

        for (String type : pageTypes[pageIndex]) {
            entries.add(new FullButtonListEntry(UIText.of(stepTypeLabelKey(type)), null, () -> openEditorForType(type))
                .setMaxButtonWidth(STEP_TYPE_BUTTON_W));
        }
    }

    @Override
    protected int getEntryHeight() {
        return UILayoutConstants.COMPACT_LIST_ENTRY_H;
    }

    @Override
    protected int fixedVisibleRowCount() {
        // Keep the selector window stable: 1 page-turn row + 6 step rows.
        return 7;
    }

    private void openPage(int newPageIndex) {
        ScreenOpener.open(new StepTypeSelectorScreen(scene, sceneIndex, parent, newPageIndex, insertAfterIndex));
    }

    private String currentPageLabel() {
        return UIText.of(pageKeys[pageIndex]) + " [" + (pageIndex + 1) + "/" + pageTypes.length + "]";
    }

    private String previousPageTooltip() {
        return pageIndex > 0 ? UIText.of(pageKeys[pageIndex - 1]) : "";
    }

    private String nextPageTooltip() {
        return pageIndex + 1 < pageTypes.length ? UIText.of(pageKeys[pageIndex + 1]) : "";
    }

    private static boolean isInterfaceStartScene(DslScene scene, int sceneIndex) {
        if (scene == null || scene.scenes == null || scene.scenes.isEmpty()) {
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
            return "show_interface".equals(step.type.toLowerCase(Locale.ROOT));
        }
        return false;
    }

    private void openEditorForType(String type) {
        if ("show_interface".equalsIgnoreCase(type)) {
            ShowInterfaceExperimentalNoticeScreen.openIfNeeded(this, () -> openEditorDirectly(type));
            return;
        }

        openEditorDirectly(type);
    }

    private void openEditorDirectly(String type) {
        AbstractStepEditorScreen editor = StepEditorFactory.createAddScreen(type, scene, sceneIndex, parent);
        if (editor != null) {
            editor.setReturnScreen(this);
            editor.setInsertAfterIndex(insertAfterIndex);
            ScreenOpener.open(editor);
        }
    }

    private String stepTypeLabelKey(String type) {
        return "ponderer.ui.step.type." + type;
    }
}
