package com.nododiiiii.ponderer.ui.catnip;

import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;

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

    private static AbstractDeclarativeConfigListScreen requireConfigScreen(AbstractDeclarativeFormScreen screen) {
        if (!(screen instanceof AbstractDeclarativeConfigListScreen configScreen)) {
            throw new IllegalStateException("ConfigEntries can only be used inside AbstractDeclarativeConfigListScreen");
        }
        return configScreen;
    }
}
