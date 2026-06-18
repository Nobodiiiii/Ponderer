package com.nododiiiii.ponderer.projector;

import java.util.Locale;

public enum ProjectorProjectionMode {
    DEFAULT,
    SCENE_ONLY,
    TEXT_ONLY;

    public boolean rendersScene() {
        return this != TEXT_ONLY;
    }

    public boolean rendersText() {
        return this != SCENE_ONLY;
    }

    public ProjectorProjectionMode next() {
        ProjectorProjectionMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "ponderer.ui.projector.projection_mode." + serializedName();
    }

    public static ProjectorProjectionMode byName(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        for (ProjectorProjectionMode value : values()) {
            if (value.serializedName().equalsIgnoreCase(raw) || value.name().equalsIgnoreCase(raw)) {
                return value;
            }
        }
        return DEFAULT;
    }
}
