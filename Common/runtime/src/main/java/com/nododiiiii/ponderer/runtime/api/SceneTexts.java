package com.nododiiiii.ponderer.runtime.api;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.scene.LocalizedText;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderTag;
import net.createmod.ponder.foundation.registration.PonderTagRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Client resources, independent of Minecraft's global language table. */
public final class SceneTexts {
    private static volatile Map<String, Map<String, Map<String, String>>> translations = Map.of();
    private static final Map<String, Map<String, Map<String, String>>> sessionTexts = new HashMap<>();
    private static final Set<PonderScene> resolvedTextScenes = Collections.newSetFromMap(new WeakHashMap<>());
    private static ResourceManager resources;
    private SceneTexts() {}

    /**
     * Opt a document-built scene into already resolved text, independently of runtime world ownership.
     * The marker survives replay on this scene, is reapplied on rebuild, and does not retain released scenes.
     */
    public static void useResolvedText(PonderScene scene) { resolvedTextScenes.add(scene); }

    public static boolean usesResolvedText(PonderScene scene) { return resolvedTextScenes.contains(scene); }

    /** Register resolved Lib text within a native storyboard without changing its other text or title. */
    public static void withResolvedText(PonderScene scene, Runnable registration) {
        boolean added = resolvedTextScenes.add(scene);
        try {
            registration.run();
        } finally {
            if (added) resolvedTextScenes.remove(scene);
        }
    }

    public static void install() {
        LocalizedText.useReferences((resource, key) -> {
            var values = new TreeMap<>(translations.getOrDefault(resource, Map.of()).getOrDefault(key, Map.of()));
            values.putAll(sessionTexts.getOrDefault(resource, Map.of()).getOrDefault(key, Map.of()));
            return values;
        });
    }
    /** Development source resources, released with the editor process rather than persisted as scene overrides. */
    public static void setSessionTexts(String scope, Map<String, Map<String, String>> locales) {
        var keys = new TreeMap<String, Map<String, String>>();
        locales.forEach((locale, values) -> values.forEach((key, text) ->
            keys.computeIfAbsent(key, ignored -> new TreeMap<>()).put(locale, text)));
        sessionTexts.put(scope, keys);
        install();
    }
    public static Map<String, Map<String, String>> sessionTexts(String scope) {
        var locales = new TreeMap<String, Map<String, String>>();
        sessionTexts.getOrDefault(scope, Map.of()).forEach((key, values) -> values.forEach((locale, text) ->
            locales.computeIfAbsent(locale, ignored -> new TreeMap<>()).put(key, text)));
        return locales;
    }
    public static void clearSessionTexts() { sessionTexts.clear(); }

    /** A higher-priority pack replaces one scene/locale file; other locale files remain available. */
    public static void reload(ResourceManager manager) {
        var next = new TreeMap<String, Map<String, Map<String, String>>>();
        for (var entry : new TreeMap<>(manager.listResources("ponderer/texts", id -> id.getPath().endsWith(".json"))).entrySet()) {
            var id = entry.getKey();
            try (var reader = entry.getValue().openAsReader()) {
                String path = id.getPath().substring("ponderer/texts/".length());
                int slash = path.lastIndexOf('/');
                if (slash <= 0) throw new IllegalArgumentException("Expected <scene>/<locale>.json");
                String scope = id.getNamespace() + ":" + path.substring(0, slash);
                String locale = path.substring(slash + 1, path.length() - 5);
                if (!locale.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Invalid locale " + locale);
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                var values = new TreeMap<String, String>();
                for (var value : json.entrySet()) {
                    LocalizedText.ref(scope, value.getKey());
                    if (!value.getValue().isJsonPrimitive() || !value.getValue().getAsJsonPrimitive().isString())
                        throw new IllegalArgumentException("Text must be a string: " + value.getKey());
                    values.put(value.getKey(), value.getValue().getAsString());
                }
                values.forEach((key, value) -> next.computeIfAbsent(scope, ignored -> new TreeMap<>())
                    .computeIfAbsent(key, ignored -> new TreeMap<>()).put(locale, value));
            } catch (Exception error) {
                LogUtils.getLogger().error("Unable to load scene texts {}: {}", id, error.toString());
            }
        }
        var snapshot = new TreeMap<String, Map<String, Map<String, String>>>();
        next.forEach((scope, keys) -> {
            var copy = new TreeMap<String, Map<String, String>>();
            keys.forEach((key, locales) -> copy.put(key, Collections.unmodifiableMap(new TreeMap<>(locales))));
            snapshot.put(scope, Collections.unmodifiableMap(copy));
        });
        translations = Collections.unmodifiableMap(snapshot);
        resources = manager;
        install();
    }

    /** /ponderer reload reads edited resource files as well as workspace documents. */
    public static void refresh() { if (resources != null) reload(resources); }

    /** Keep the original tag's icon and item bindings while resolving its own scene text resources. */
    public static void localizeTag(ResourceLocation id, LocalizedText title, LocalizedText description) {
        var registry = (PonderTagRegistry) PonderIndex.getTagAccess();
        var original = registry.getRegisteredTag(id);
        var localized = new PonderTag(id, null, ItemStack.EMPTY, original.getMainItem()) {
            @Override public String getTitle() { return title == null ? original.getTitle() : title.resolve(); }
            @Override public String getDescription() { return description == null ? original.getDescription() : description.resolve(); }
            @Override public void render(GuiGraphics graphics, int x, int y) { original.render(graphics, x, y); }
        };
        registry.registerTag(localized);
        registry.getListedTags().replaceAll(tag -> tag.getId().equals(id) ? localized : tag);
    }
}
