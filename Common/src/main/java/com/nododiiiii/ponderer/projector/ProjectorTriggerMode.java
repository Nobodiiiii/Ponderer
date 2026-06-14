package com.nododiiiii.ponderer.projector;

import java.util.Locale;

public enum ProjectorTriggerMode {
    MANUAL_LOOP,
    MANUAL_ONCE,
    REDSTONE_RISING_ONCE,
    REDSTONE_POWERED_LOOP;

    public boolean loops() {
        return this == MANUAL_LOOP || this == REDSTONE_POWERED_LOOP;
    }

    public boolean isManual() {
        return this == MANUAL_LOOP || this == MANUAL_ONCE;
    }

    public boolean usesRedstone() {
        return this == REDSTONE_RISING_ONCE || this == REDSTONE_POWERED_LOOP;
    }

    public boolean startsOnRedstoneRisingEdge() {
        return this == REDSTONE_RISING_ONCE;
    }

    public boolean runsWhilePowered() {
        return this == REDSTONE_POWERED_LOOP;
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "ponderer.ui.projector.trigger_mode." + serializedName();
    }

    public static ProjectorTriggerMode fromFields(boolean redstoneMode, boolean loopMode) {
        if (redstoneMode) {
            return loopMode ? REDSTONE_POWERED_LOOP : REDSTONE_RISING_ONCE;
        }
        return loopMode ? MANUAL_LOOP : MANUAL_ONCE;
    }

    public static ProjectorTriggerMode byName(String raw) {
        if (raw == null || raw.isBlank()) {
            return MANUAL_LOOP;
        }
        for (ProjectorTriggerMode value : values()) {
            if (value.serializedName().equalsIgnoreCase(raw) || value.name().equalsIgnoreCase(raw)) {
                return value;
            }
        }
        return MANUAL_LOOP;
    }
}
