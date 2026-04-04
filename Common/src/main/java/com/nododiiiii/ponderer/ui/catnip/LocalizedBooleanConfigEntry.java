package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.config.ui.entries.BooleanEntry;
import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;

public class LocalizedBooleanConfigEntry extends BooleanEntry implements SearchableListEntry {

    private final String searchText;

    public LocalizedBooleanConfigEntry(String labelKey, @Nullable String tooltipKey,
                                       ForgeConfigSpec.ConfigValue<Boolean> value, ForgeConfigSpec.ValueSpec spec) {
        super(com.nododiiiii.ponderer.ui.UIText.of(labelKey), value, spec);
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

    @Override
    protected int getLabelWidth(int totalWidth) {
        return EntryTextSupport.compactLabelWidth(totalWidth);
    }
}
