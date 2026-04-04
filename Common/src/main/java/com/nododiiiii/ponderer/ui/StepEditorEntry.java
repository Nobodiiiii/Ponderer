package com.nododiiiii.ponderer.ui;

import java.util.Map;

public interface StepEditorEntry {

    int rows();

    void build(AbstractStepEditorScreen screen);

    default void snapshot(Map<String, String> snapshot) {
    }

    default void restore(Map<String, String> snapshot) {
    }
}
