package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeFormScreen;

public interface StepEditorEntry extends FieldSpec {

    void buildStep(AbstractStepEditorScreen screen);

    @Override
    default void build(AbstractDeclarativeFormScreen screen) {
        buildStep((AbstractStepEditorScreen) screen);
    }
}
