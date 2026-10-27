package com.nododiiiii.ponderer.application.ui.widgets;

public interface SearchableListEntry {
    boolean matchesQuery(String query);

    void highlightEntry();
}
