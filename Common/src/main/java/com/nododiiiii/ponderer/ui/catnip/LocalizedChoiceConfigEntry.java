package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.config.ui.ConfigHelper;
import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;
import java.util.List;

public class LocalizedChoiceConfigEntry extends ButtonListEntry {

    public LocalizedChoiceConfigEntry(String labelKey, @Nullable String tooltipKey, int buttonWidth,
                                      ForgeConfigSpec.ConfigValue<String> value, ForgeConfigSpec.ValueSpec spec,
                                      List<String> optionLabelKeys, List<String> optionValues) {
        this(labelKey, tooltipKey, buttonWidth, value, spec, optionLabelKeys, optionValues, null);
    }

    public LocalizedChoiceConfigEntry(String labelKey, @Nullable String tooltipKey, int buttonWidth,
                                      ForgeConfigSpec.ConfigValue<String> value, ForgeConfigSpec.ValueSpec spec,
                                      List<String> optionLabelKeys, List<String> optionValues,
                                      @Nullable Runnable onChanged) {
        this(labelKey, tooltipKey, buttonWidth, value, optionLabelKeys, optionValues,
            ConfigEntrySupport.metadataOf(value, spec), onChanged);
    }

    private LocalizedChoiceConfigEntry(String labelKey, @Nullable String tooltipKey, int buttonWidth,
                                       ForgeConfigSpec.ConfigValue<String> value,
                                       List<String> optionLabelKeys, List<String> optionValues,
                                       ConfigEntrySupport.Metadata metadata, @Nullable Runnable onChanged) {
        super(labelKey, tooltipKey, buttonWidth,
            () -> {
                cycleValue(value, optionValues, metadata);
                if (onChanged != null) {
                    onChanged.run();
                }
            },
            () -> currentLabel(value, optionLabelKeys, optionValues, metadata),
            () -> 0xFFFFFF,
            (String) null);
        this.path = metadata.path();
        this.annotations.putAll(metadata.annotations());
        setHalfWidthControl(buttonWidth);
    }

    private static void cycleValue(ForgeConfigSpec.ConfigValue<String> value, List<String> optionValues,
                                   ConfigEntrySupport.Metadata metadata) {
        String current = ConfigEntrySupport.currentValue(metadata, value);
        int currentIndex = optionValues.indexOf(current);
        int nextIndex = (Math.max(currentIndex, 0) + 1) % optionValues.size();
        ConfigHelper.setValue(metadata.path(), value, optionValues.get(nextIndex), metadata.annotations());
    }

    private static String currentLabel(ForgeConfigSpec.ConfigValue<String> value, List<String> optionLabelKeys,
                                       List<String> optionValues, ConfigEntrySupport.Metadata metadata) {
        String current = ConfigEntrySupport.currentValue(metadata, value);
        int index = optionValues.indexOf(current);
        if (index >= 0 && index < optionLabelKeys.size()) {
            return com.nododiiiii.ponderer.ui.UIText.of(optionLabelKeys.get(index));
        }
        return current;
    }
}
