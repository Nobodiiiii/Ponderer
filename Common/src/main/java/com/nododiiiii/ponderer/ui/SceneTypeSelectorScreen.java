package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ui.catnip.AbstractReadonlyDeclarativeListScreen;
import com.nododiiiii.ponderer.ui.catnip.FullButtonListEntry;
import com.nododiiiii.ponderer.ui.catnip.SectionHeaderListEntry;
import net.createmod.catnip.config.ui.ConfigScreenList;

import java.util.List;

public class SceneTypeSelectorScreen extends AbstractReadonlyDeclarativeListScreen {

    private final SceneEditorScreen parent;

    public SceneTypeSelectorScreen(SceneEditorScreen parent) {
        super(parent, "ponderer.ui.scope.editor", "ponderer.ui.scene_selector.title", 320);
        this.parent = parent;
    }

    @Override
    protected void collectEntries(List<ConfigScreenList.Entry> entries) {
        entries.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.scene_selector.desc")));
        entries.add(new FullButtonListEntry(
            UIText.of("ponderer.ui.step.type.show_structure"),
            null,
            () -> parent.insertSplitStep("show_structure")));
        entries.add(new FullButtonListEntry(
            UIText.of("ponderer.ui.step.type.show_interface"),
            null,
            () -> parent.insertSplitStep("show_interface")));
    }

    @Override
    protected int getEntryHeight() {
        return 36;
    }
}
