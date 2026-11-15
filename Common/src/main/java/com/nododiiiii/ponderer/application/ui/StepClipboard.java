package com.nododiiiii.ponderer.application.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.scene.LocalizedText;

import java.util.List;
import java.util.UUID;

/** Shared step clipboard for scene lists and nested tutorial lists. */
final class StepClipboard {
    private static final Gson JSON = new GsonBuilder()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter()).create();
    private static List<DslScene.DslStep> contents = List.of();
    private static Object owner;

    private StepClipboard() {}

    static List<DslScene.DslStep> contents() { return contents; }

    static boolean isOwnedBy(Object source) { return owner == source; }

    static void setContents(Object source, List<DslScene.DslStep> steps) {
        owner = source;
        contents = List.copyOf(steps);
    }

    static DslScene.DslStep copy(DslScene.DslStep step) {
        DslScene.DslStep copy = JSON.fromJson(JSON.toJson(step), DslScene.DslStep.class);
        detach(copy);
        return copy;
    }

    private static void detach(DslScene.DslStep copy) {
        copy.id = UUID.randomUUID().toString();
        // A pasted step owns its text, including translations supplied by an exported resource.
        copy.text = detachText(copy.text);
        copy.hint = detachText(copy.hint);
        if (copy.steps != null) copy.steps.stream().filter(java.util.Objects::nonNull).forEach(StepClipboard::detach);
    }

    private static LocalizedText detachText(LocalizedText text) {
        if (text == null || !text.isReference()) return text;
        var translations = text.getAllTranslations();
        // Keep unresolved references so saving reports the missing resource instead of losing text.
        return translations.isEmpty() ? text : LocalizedText.ofMap(translations);
    }
}
