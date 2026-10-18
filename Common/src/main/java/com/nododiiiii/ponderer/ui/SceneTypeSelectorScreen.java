package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ui.catnip.AbstractReadonlyDeclarativeListScreen;
import com.nododiiiii.ponderer.ui.catnip.FullButtonListEntry;
import com.nododiiiii.ponderer.ui.catnip.SectionHeaderListEntry;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;
import java.util.List;

public class SceneTypeSelectorScreen extends AbstractReadonlyDeclarativeListScreen {

    private final SceneEditorScreen sceneEditor;

    public SceneTypeSelectorScreen(SceneEditorScreen sceneEditor) {
        this(sceneEditor, sceneEditor);
    }

    public SceneTypeSelectorScreen(SceneEditorScreen sceneEditor, @Nullable Screen returnScreen) {
        super(returnScreen, "ponderer.ui.scope.editor", "ponderer.ui.scene_selector", UILayoutConstants.EDITOR_LIST_W);
        this.sceneEditor = sceneEditor;
    }

    @Override
    protected void collectEntries(List<ConfigScreenList.Entry> entries) {
        entries.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.scene_selector.desc")));
        entries.add(new FullButtonListEntry(
            UIText.of("ponderer.ui.step.type.show_structure"),
            null,
            () -> sceneEditor.insertSplitStep("show_structure")));
        entries.add(new FullButtonListEntry(
            UIText.of("ponderer.ui.step.type.show_interface"),
            null,
            () -> ShowInterfaceExperimentalNoticeScreen.openIfNeeded(this, () -> sceneEditor.insertSplitStep("show_interface"))));
    }

    @Override
    protected int getEntryHeight() {
        return UILayoutConstants.COMPACT_LIST_ENTRY_H;
    }
}
