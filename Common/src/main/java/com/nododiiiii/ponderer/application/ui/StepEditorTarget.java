package com.nododiiiii.ponderer.application.ui;

import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.application.ui.widgets.DeclarativeFormEntry;
import net.minecraft.client.gui.screens.Screen;
import java.util.List;

/** Redirects nested editors to an unsaved owner instead of the workspace document. */
interface StepEditorTarget {
    Screen returnScreen();
    void save(int editIndex, int afterIndex, DslScene.DslStep step);
    default void configureFormState(AbstractStepEditorScreen editor, List<SnapshotParticipant> participants) {}
    default void collectFormEntries(AbstractStepEditorScreen editor, List<DeclarativeFormEntry> entries) {}
}
