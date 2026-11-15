package com.nododiiiii.ponderer.scene;

import java.util.*;

/** Extracts portable per-scene text files. Existing semantic keys survive reordering and edits. */
public record SceneTextBundle(DslScene document, Map<String, Map<String, String>> locales) {
    public static SceneTextBundle extract(DslScene source) {
        DslScene document = SceneCodec.copy(source);
        var locales = new TreeMap<String, Map<String, String>>();
        var reserved = new HashSet<String>();
        visit(document, (text, suggested) -> {
            if (text != null && text.isReference()) reserved.add(text.key());
            return text;
        });
        var generated = new HashSet<String>();
        var assigned = new HashMap<String, Map<String, String>>();
        visit(document, (text, suggested) -> {
            if (text == null) return null;
            Map<String, String> values = text.getAllTranslations();
            if (text.isReference() && values.isEmpty())
                throw new IllegalArgumentException("Missing text resource " + text.resource() + "#" + text.key());
            if (values.isEmpty()) return text;
            String key;
            // Older copies can still share a key. Fork it when their translations diverge.
            if (text.isReference() && (!assigned.containsKey(text.key()) || assigned.get(text.key()).equals(values)))
                key = text.key();
            else {
                key = suggested;
                for (int n = 2; reserved.contains(key) || generated.contains(key); n++) key = suggested + "_" + n;
                generated.add(key);
            }
            assigned.putIfAbsent(key, values);
            for (var entry : values.entrySet()) {
                String locale = entry.getKey().equals("_plain") ? "en_us" : entry.getKey();
                if (!locale.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Invalid text locale: " + locale);
                String previous = locales.computeIfAbsent(locale, ignored -> new TreeMap<>()).putIfAbsent(key, entry.getValue());
                if (previous != null && !previous.equals(entry.getValue()))
                    throw new IllegalArgumentException("Conflicting text key " + document.id + "#" + key + " (" + locale + ")");
            }
            return LocalizedText.ref(document.id, key);
        });
        return new SceneTextBundle(document, locales);
    }

    /** Used by exporters and editor tooling; opaque custom Java parameters are never inspected. */
    public static void visit(DslScene document, java.util.function.BiFunction<LocalizedText, String, LocalizedText> visitor) {
        document.title = visitor.apply(document.title, "title");
        if (document.customTags != null) document.customTags.forEach((id, tag) -> {
            if (tag == null) return;
            String prefix = "tags." + id.replace(':', '.');
            tag.title = visitor.apply(tag.title, prefix + ".title");
            tag.description = visitor.apply(tag.description, prefix + ".description");
        });
        if (document.scenes == null) return;
        for (int i = 0; i < document.scenes.size(); i++) {
            var segment = document.scenes.get(i);
            if (segment == null) continue;
            String prefix = segment.id == null ? "scene_" + (i + 1) : segment.id;
            segment.title = visitor.apply(segment.title, prefix + ".title");
            visitSteps(segment.steps, prefix, new int[]{0, 0}, visitor);
        }
    }

    private static void visitSteps(List<DslScene.DslStep> steps, String prefix, int[] counts,
                                   java.util.function.BiFunction<LocalizedText, String, LocalizedText> visitor) {
        if (steps == null) return;
        for (var step : steps) {
            if (step == null) continue;
            if (step.text != null) step.text = visitor.apply(step.text, prefix + ".text_" + ++counts[0]);
            if (step.hint != null) step.hint = visitor.apply(step.hint, prefix + ".hint_" + ++counts[1]);
            visitSteps(step.steps, prefix, counts, visitor);
        }
    }
}
