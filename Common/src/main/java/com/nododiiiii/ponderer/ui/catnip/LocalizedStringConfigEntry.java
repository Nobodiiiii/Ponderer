package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.config.ui.ConfigHelper;
import net.neoforged.neoforge.common.ModConfigSpec;

import javax.annotation.Nullable;

public class LocalizedStringConfigEntry extends PlainTextListEntry {

    public LocalizedStringConfigEntry(String labelKey, @Nullable String hintKey, @Nullable String tooltipKey,
                                      ModConfigSpec.ConfigValue<String> value, ModConfigSpec.ValueSpec spec) {
        this(labelKey, hintKey, tooltipKey, value, ConfigEntrySupport.metadataOf(value, spec));
    }

    private LocalizedStringConfigEntry(String labelKey, @Nullable String hintKey, @Nullable String tooltipKey,
                                       ModConfigSpec.ConfigValue<String> value,
                                       ConfigEntrySupport.Metadata metadata) {
        super(labelKey, tooltipKey, hintKey, ConfigEntrySupport.currentValue(metadata, value), newValue -> {
        });
        this.path = metadata.path();
        this.annotations.putAll(metadata.annotations());
        field().setResponder(newValue -> ConfigHelper.setValue(metadata.path(), value, newValue, metadata.annotations()));
    }
}
