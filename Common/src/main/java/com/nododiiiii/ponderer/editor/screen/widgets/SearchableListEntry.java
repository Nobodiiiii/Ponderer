package com.nododiiiii.ponderer.editor.screen.widgets;

public interface SearchableListEntry {
    boolean matchesQuery(String query);

    void highlightEntry();
}
