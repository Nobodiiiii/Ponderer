package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.config.ui.entries.NumberEntry;
import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;

public class LocalizedIntegerConfigEntry extends NumberEntry.IntegerEntry implements SearchableListEntry {

    private final String searchText;

    public LocalizedIntegerConfigEntry(String labelKey, @Nullable String hintKey, @Nullable String tooltipKey,
                                       ForgeConfigSpec.ConfigValue<Integer> value, ForgeConfigSpec.ValueSpec spec) {
        super(com.nododiiiii.ponderer.ui.UIText.of(labelKey), value, spec);
        this.searchText = EntryTextSupport.createSearchText(labelKey, tooltipKey);
        EntryTextSupport.applyTooltip(this, labelKey, tooltipKey);
        EntryTextSupport.applyHint(textField, hintKey);
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
