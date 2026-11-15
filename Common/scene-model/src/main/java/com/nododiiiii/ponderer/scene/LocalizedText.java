package com.nododiiiii.ponderer.scene;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.util.function.Supplier;

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
    private String reference;
    private String key;
    private static volatile java.util.function.BiFunction<String, String, Map<String, String>> references = (resource, key) -> Map.of();

    private static Supplier<String> language = () -> "en_us";

    /** Install the host language lookup at client bootstrap; no game classes are loaded by the model. */
    public static void useLanguage(Supplier<String> supplier) {
        language = java.util.Objects.requireNonNull(supplier);
    }

    /** The client resource loader supplies translations; the model itself has no game dependency. */
    public static void useReferences(java.util.function.BiFunction<String, String, Map<String, String>> resolver) {
        references = java.util.Objects.requireNonNull(resolver);
    }

    public static LocalizedText ref(String resource, String key) {
        if (resource == null || !resource.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")
            || java.util.Arrays.asList(resource.substring(resource.indexOf(':') + 1).split("/", -1)).stream()
                .anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals("..")))
            throw new IllegalArgumentException("Invalid text resource: " + resource);
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Blank text key");
        var text = new LocalizedText();
        text.reference = resource;
        text.key = key;
        text.localized = new LinkedHashMap<>();
        return text;
    }

    public boolean isReference() { return reference != null; }
    public String resource() { return reference; }
    public String key() { return key; }
    public LocalizedText copy() {
        if (!isReference()) return isPlain() ? of(plain) : ofMap(localized == null ? Map.of() : localized);
        var result = ref(reference, key);
        result.localized.putAll(localized);
        return result;
    }

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
        if (isReference()) {
            var translations = new LinkedHashMap<>(references.apply(reference, key));
            translations.putAll(localized);
            return translations;
        }
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
        return resolve(getCurrentLanguage());
    }

    /**
     * Resolve for a specific language with the same fallback chain.
     */
    public String resolve(String lang) {
        if (plain != null) return plain;
        Map<String, String> translations = isReference() ? getAllTranslations() : localized;
        if (translations == null || translations.isEmpty()) return isReference() ? reference + "#" + key : "";

        String val = translations.get(lang);
        if (val != null) return val;

        val = translations.get("en_us");
        if (val != null) return val;

        return translations.values().iterator().next();
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
        if (isReference()) return getAllTranslations().get(lang);
        if (plain != null) {
            // Plain text is treated as unlocalized; return it for any language query
            return plain;
        }
        if (localized == null) return null;
        return localized.get(lang);
    }

    /** Check if the text is null/empty in all forms. */
    public boolean isEmpty() {
        if (isReference()) return false;
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
            return language.get();
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
            if (value.isReference()) {
                out.beginObject();
                out.name("ref").value(value.reference);
                out.name("key").value(value.key);
                if (!value.localized.isEmpty()) {
                    out.name("overrides").beginObject();
                    for (var entry : value.localized.entrySet()) out.name(entry.getKey()).value(entry.getValue());
                    out.endObject();
                }
                out.endObject();
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
                var object = JsonParser.parseReader(in).getAsJsonObject();
                if (object.has("ref")) {
                    var text = LocalizedText.ref(object.get("ref").getAsString(), object.get("key").getAsString());
                    if (object.has("overrides")) object.getAsJsonObject("overrides").entrySet()
                        .forEach(entry -> text.setForLang(entry.getKey(), entry.getValue().getAsString()));
                    return text;
                }
                Map<String, String> map = new LinkedHashMap<>();
                for (var entry : object.entrySet()) {
                    if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString())
                        map.put(entry.getKey(), entry.getValue().getAsString());
                    else if (entry.getKey().equals("localized") && entry.getValue().isJsonObject())
                        entry.getValue().getAsJsonObject().entrySet().stream()
                            .filter(child -> child.getValue().isJsonPrimitive() && child.getValue().getAsJsonPrimitive().isString())
                            .forEach(child -> map.put(child.getKey(), child.getValue().getAsString()));
                }
                return LocalizedText.ofMap(map);
            }
            // unexpected token - skip and return empty
            in.skipValue();
            return LocalizedText.of("");
        }
    }
}
