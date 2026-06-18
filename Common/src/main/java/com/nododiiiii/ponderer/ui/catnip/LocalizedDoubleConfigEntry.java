package com.nododiiiii.ponderer.ui.catnip;

import com.nododiiiii.ponderer.ui.UIText;
import net.createmod.catnip.config.ui.entries.NumberEntry;
import net.neoforged.neoforge.common.ModConfigSpec;

import javax.annotation.Nullable;

public class LocalizedDoubleConfigEntry extends NumberEntry.DoubleEntry implements SearchableListEntry {

    private final String searchText;

    public LocalizedDoubleConfigEntry(String labelKey, @Nullable String tooltipKey,
                                      ModConfigSpec.DoubleValue value, ModConfigSpec.ValueSpec spec) {
        super(UIText.of(labelKey), value, spec);
        this.searchText = EntryTextSupport.createSearchText(labelKey, tooltipKey);
        EntryTextSupport.applyTooltip(this, labelKey, tooltipKey);
    }

    @Override
    public boolean matchesQuery(String query) {
        return searchText.contains(query);
    }

    @Override
    public void highlightEntry() {
        annotations.put("highlight", ":)");
    }
}
