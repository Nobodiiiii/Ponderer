package com.nododiiiii.ponderer.ui.catnip;

public interface SearchableListEntry {
    boolean matchesQuery(String query);

    void highlightEntry();
}
