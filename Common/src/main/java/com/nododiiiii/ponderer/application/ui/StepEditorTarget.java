package com.nododiiiii.ponderer.application.ui;

import com.nododiiiii.ponderer.scene.DslScene;
import net.minecraft.client.gui.screens.Screen;

/** Redirects nested editors to an unsaved owner instead of the workspace document. */
interface StepEditorTarget {
    Screen returnScreen();
    void save(int editIndex, int afterIndex, DslScene.DslStep step);
}
