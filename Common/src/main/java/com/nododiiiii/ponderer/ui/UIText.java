package com.nododiiiii.ponderer.ui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.resources.language.I18n;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * Simple i18n helper to translate UI text using the mod's lang files.
 * All keys use the "ponderer.ui." prefix.
 */
public final class UIText {
    private static final String FALLBACK_LANG_PATH = "/assets/ponderer/lang/en_us.json";
    private static final Map<String, String> FALLBACK_EN = loadFallbackEn();

    private UIText() {}

    /** Translate a key like "ponderer.ui.xxx" */
    public static String of(String key) {
        String translated = I18n.get(key);
        if (!key.equals(translated)) return translated;
        return fallbackTranslate(key, null);
    }

    /** Translate a key with format args, like "ponderer.ui.xxx" with %s */
    public static String of(String key, Object... args) {
        String translated = I18n.get(key, args);
        if (!key.equals(translated)) return translated;
        return fallbackTranslate(key, args);
    }

    private static String fallbackTranslate(String key, @Nullable Object[] args) {
        String template = FALLBACK_EN.get(key);
        if (template == null) return key;
        if (args == null || args.length == 0) return template;
        try {
            return String.format(template, args);
        } catch (Exception ignored) {
            return template;
        }
    }

    private static Map<String, String> loadFallbackEn() {
        try (InputStream is = UIText.class.getResourceAsStream(FALLBACK_LANG_PATH)) {
            if (is == null) return Collections.emptyMap();
            try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                java.lang.reflect.Type type = new TypeToken<Map<String, String>>() {}.getType();
                Map<String, String> map = new Gson().fromJson(reader, type);
                return map == null ? Collections.emptyMap() : map;
            }
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }
}
