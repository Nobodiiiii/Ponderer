package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeFormScreen;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;

import java.util.Map;

public interface StepEditorEntry extends DeclarativeFormEntry {

    int rows();

    void buildStep(AbstractStepEditorScreen screen);

    @Override
    default void build(AbstractDeclarativeFormScreen screen) {
        buildStep((AbstractStepEditorScreen) screen);
    }

    default void snapshot(Map<String, String> snapshot) {
    }

    default void restore(Map<String, String> snapshot) {
    }
}
