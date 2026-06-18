package com.nododiiiii.ponderer.ui.catnip;

import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;
import java.util.List;

public final class ConfigEntries {

    private ConfigEntries() {
    }

    public static DeclarativeFormEntry stringEntry(String labelKey, @Nullable String hintKey,
                                                   @Nullable String tooltipKey,
                                                   ForgeConfigSpec.ConfigValue<String> value) {
        return screen -> requireConfigScreen(screen).addStringConfigEntry(labelKey, hintKey, tooltipKey, value);
    }

    public static DeclarativeFormEntry booleanEntry(String labelKey, @Nullable String tooltipKey,
                                                    ForgeConfigSpec.ConfigValue<Boolean> value) {
        return screen -> requireConfigScreen(screen).addBooleanConfigEntry(labelKey, tooltipKey, value);
    }

    public static DeclarativeFormEntry integerEntry(String labelKey, @Nullable String hintKey,
                                                    @Nullable String tooltipKey,
                                                    ForgeConfigSpec.ConfigValue<Integer> value) {
        return screen -> requireConfigScreen(screen).addIntegerConfigEntry(labelKey, hintKey, tooltipKey, value);
    }

    public static DeclarativeFormEntry doubleEntry(String labelKey, @Nullable String tooltipKey,
                                                   ForgeConfigSpec.DoubleValue value) {
        return screen -> requireConfigScreen(screen).addDoubleConfigEntry(labelKey, tooltipKey, value);
    }

    public static DeclarativeFormEntry choiceEntry(String labelKey, @Nullable String tooltipKey,
                                                   ForgeConfigSpec.ConfigValue<String> value,
                                                   int buttonWidth,
                                                   List<String> optionLabelKeys,
                                                   List<String> optionValues) {
        return choiceEntry(labelKey, tooltipKey, value, buttonWidth, optionLabelKeys, optionValues, null);
    }

    public static DeclarativeFormEntry choiceEntry(String labelKey, @Nullable String tooltipKey,
                                                   ForgeConfigSpec.ConfigValue<String> value,
                                                   int buttonWidth,
                                                   List<String> optionLabelKeys,
                                                   List<String> optionValues,
                                                   @Nullable Runnable onChanged) {
        return screen -> requireConfigScreen(screen)
            .addChoiceConfigEntry(labelKey, tooltipKey, buttonWidth, value, optionLabelKeys, optionValues, onChanged);
    }

    private static AbstractDeclarativeConfigListScreen requireConfigScreen(AbstractDeclarativeFormScreen screen) {
        if (!(screen instanceof AbstractDeclarativeConfigListScreen configScreen)) {
            throw new IllegalStateException("ConfigEntries can only be used inside AbstractDeclarativeConfigListScreen");
        }
        return configScreen;
    }
}
