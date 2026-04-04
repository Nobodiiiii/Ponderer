package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.config.ui.entries.StringEntry;
import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;

public class LocalizedStringConfigEntry extends StringEntry implements SearchableListEntry {

    private final String searchText;

    public LocalizedStringConfigEntry(String labelKey, @Nullable String hintKey, @Nullable String tooltipKey,
                                      ForgeConfigSpec.ConfigValue<String> value, ForgeConfigSpec.ValueSpec spec) {
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
