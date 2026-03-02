package com.nododiiiii.ponderer.ponder;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A text value that can be either a plain string or a map of language code to string.
 * <p>JSON examples:</p>
 * <pre>
 *   "text": "Hello"                                       // plain string
 *   "text": {"en_us": "Hello", "zh_cn": "你好"}            // localized
 * </pre>
 * <p>Resolution order: user language -> en_us -> first available -> ""</p>
 */
public final class LocalizedText {

    /** null if this is a localized map; the plain text otherwise */
    private String plain;
    /** null if this is a plain string; the lang->text map otherwise */
    private Map<String, String> localized;

    private LocalizedText() {}

    /** Create a plain (non-localized) text. */
    public static LocalizedText of(String text) {
        LocalizedText lt = new LocalizedText();
        lt.plain = text != null ? text : "";
        return lt;
    }

    /** Create a localized text from a language map. */
    public static LocalizedText ofMap(Map<String, String> map) {
        LocalizedText lt = new LocalizedText();
        lt.localized = new LinkedHashMap<>(map);
        return lt;
    }

    /** Whether this is a plain (non-localized) string. */
    public boolean isPlain() {
        return plain != null;
    }

    /** Get the map of all translations. Returns a single-entry map for plain text. */
    public Map<String, String> getAllTranslations() {
        if (localized != null) return localized;
        Map<String, String> m = new LinkedHashMap<>();
        m.put("_plain", plain != null ? plain : "");
        return m;
    }

    /**
     * Resolve to the best available string.
     * Order: current MC language -> en_us -> first available -> ""
     */
    public String resolve() {
        if (plain != null) return plain;
        if (localized == null || localized.isEmpty()) return "";

        String lang = getCurrentLanguage();

        // 1. Try current language
        String val = localized.get(lang);
        if (val != null) return val;

        // 2. Try en_us
        val = localized.get("en_us");
        if (val != null) return val;

        // 3. First available
        return localized.values().iterator().next();
    }

    /**
     * Resolve for a specific language with the same fallback chain.
     */
    public String resolve(String lang) {
        if (plain != null) return plain;
        if (localized == null || localized.isEmpty()) return "";

        String val = localized.get(lang);
        if (val != null) return val;

        val = localized.get("en_us");
        if (val != null) return val;

        return localized.values().iterator().next();
    }

    /**
     * Get the text for the current language, or the resolved fallback.
     * Use in editors to show the "current" text.
     */
    public String getForCurrentLang() {
        return resolve();
    }

    /**
     * Set the text for the current language.
     * If this was a plain string and the current lang is not en_us,
     * it will be upgraded to a localized map (keeping the old plain as en_us).
     */
    public void setForCurrentLang(String text) {
        setForLang(getCurrentLanguage(), text);
    }

    /**
     * Set the text for a specific language.
     * If this was a plain string and the target lang is not en_us,
     * it will be upgraded to a localized map (keeping the old plain as en_us).
     */
    public void setForLang(String lang, String text) {
        if (plain != null) {
            if ("en_us".equals(lang)) {
                plain = text;
            } else {
                localized = new LinkedHashMap<>();
                if (!plain.isEmpty()) {
                    localized.put("en_us", plain);
                }
                localized.put(lang, text);
                plain = null;
            }
        } else {
            if (localized == null) localized = new LinkedHashMap<>();
            localized.put(lang, text);
        }
    }

    /**
     * Get the text stored for a specific language exactly (no fallback).
     * Returns null if no text is stored for that language.
     * For plain text, returns the plain text only when lang is "en_us" or null otherwise.
     */
    public String getExact(String lang) {
        if (plain != null) {
            // Plain text is treated as unlocalized; return it for any language query
            return plain;
        }
        if (localized == null) return null;
        return localized.get(lang);
    }

    /** Check if the text is null/empty in all forms. */
    public boolean isEmpty() {
        if (plain != null) return plain.isEmpty();
        if (localized == null || localized.isEmpty()) return true;
        return localized.values().stream().allMatch(v -> v == null || v.isEmpty());
    }

    @Override
    public String toString() {
        return resolve();
    }

    private static String getCurrentLanguage() {
        try {
            return Minecraft.getInstance().getLanguageManager().getSelected();
        } catch (Exception e) {
            return "en_us";
        }
    }

    // ---- Gson TypeAdapter ----

    public static class GsonAdapter extends TypeAdapter<LocalizedText> {
        @Override
        public void write(JsonWriter out, LocalizedText value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            if (value.plain != null) {
                out.value(value.plain);
            } else if (value.localized != null) {
                out.beginObject();
                for (Map.Entry<String, String> e : value.localized.entrySet()) {
                    out.name(e.getKey()).value(e.getValue());
                }
                out.endObject();
            } else {
                out.value("");
            }
        }

        @Override
        public LocalizedText read(JsonReader in) throws IOException {
            JsonToken token = in.peek();
            if (token == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            if (token == JsonToken.STRING) {
                return LocalizedText.of(in.nextString());
            }
            if (token == JsonToken.BEGIN_OBJECT) {
                Map<String, String> map = new LinkedHashMap<>();
                in.beginObject();
                while (in.hasNext()) {
                    String key = in.nextName();
                    JsonToken valueToken = in.peek();
                    if (valueToken == JsonToken.STRING) {
                        map.put(key, in.nextString());
                        continue;
                    }

                    // Backward-compatibility for malformed shape:
                    // { "localized": { "en_us": "...", "zh_cn": "..." } }
                    if ("localized".equals(key) && valueToken == JsonToken.BEGIN_OBJECT) {
                        in.beginObject();
                        while (in.hasNext()) {
                            String lang = in.nextName();
                            JsonToken nested = in.peek();
                            if (nested == JsonToken.STRING) {
                                map.put(lang, in.nextString());
                            } else {
                                in.skipValue();
                            }
                        }
                        in.endObject();
                        continue;
                    }

                    in.skipValue();
                }
                in.endObject();
                return LocalizedText.ofMap(map);
            }
            // unexpected token - skip and return empty
            in.skipValue();
            return LocalizedText.of("");
        }
    }
}
