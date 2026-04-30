package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.config.ui.ConfigHelper;
import net.neoforged.neoforge.common.ModConfigSpec;

import javax.annotation.Nullable;

public class LocalizedBooleanConfigEntry extends ToggleListEntry {

    public LocalizedBooleanConfigEntry(String labelKey, @Nullable String tooltipKey,
                                       ModConfigSpec.ConfigValue<Boolean> value, ModConfigSpec.ValueSpec spec) {
        this(labelKey, tooltipKey, value, ConfigEntrySupport.metadataOf(value, spec));
    }

    private LocalizedBooleanConfigEntry(String labelKey, @Nullable String tooltipKey,
                                        ModConfigSpec.ConfigValue<Boolean> value,
                                        ConfigEntrySupport.Metadata metadata) {
        super(labelKey, tooltipKey,
            () -> ConfigEntrySupport.currentValue(metadata, value),
            () -> ConfigHelper.setValue(
                metadata.path(),
                value,
                !ConfigEntrySupport.currentValue(metadata, value),
                metadata.annotations()));
        this.path = metadata.path();
        this.annotations.putAll(metadata.annotations());
    }
}
