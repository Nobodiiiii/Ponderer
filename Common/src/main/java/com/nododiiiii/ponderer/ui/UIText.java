package com.nododiiiii.ponderer.ui;

import net.minecraft.client.resources.language.I18n;

/**
 * Simple i18n helper to translate UI text using the mod's lang files.
 * All keys use the "ponderer.ui." prefix.
 */
public final class UIText {
    private UIText() {}

    /** Translate a key like "ponderer.ui.xxx" */
    public static String of(String key) {
        return I18n.get(key);
    }

    /** Translate a key with format args, like "ponderer.ui.xxx" with %s */
    public static String of(String key, Object... args) {
        return I18n.get(key, args);
    }
}
