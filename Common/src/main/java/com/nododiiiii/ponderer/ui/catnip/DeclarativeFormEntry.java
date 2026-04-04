package com.nododiiiii.ponderer.ui.catnip;

import java.util.Map;

public interface DeclarativeFormEntry {

    void build(AbstractDeclarativeFormScreen screen);

    default void snapshot(Map<String, String> snapshot) {
    }

    default void restore(Map<String, String> snapshot) {
    }
}
