package com.nododiiiii.ponderer.runtime.api;

import java.util.Locale;
import java.util.Set;

/** Startup capabilities; independent of Minecraft and loader classes. */
public enum RuntimeFeature {
    CORE("core"),
    VIEW("view", CORE),
    TEXT("text", VIEW),
    PLAYER("player", VIEW, TEXT),
    INTERFACES("interfaces", VIEW),
    NBT_BINDINGS("nbt_bindings", CORE),
    RENDER_CACHE("render_cache", CORE),
    DETACHED_RENDERING("detached_rendering", VIEW, TEXT, RENDER_CACHE),
    UI_EXTENSIONS("ui_extensions", CORE),
    APPLICATION("application", CORE),
    EDITOR("editor", APPLICATION, DETACHED_RENDERING, UI_EXTENSIONS),
    FEEDBACK("feedback");

    private final String key;
    private final Set<RuntimeFeature> dependencies;
    RuntimeFeature(String key, RuntimeFeature... dependencies) {
        this.key = key;
        this.dependencies = Set.of(dependencies);
    }
    public String key() { return key; }
    public Set<RuntimeFeature> dependencies() { return dependencies; }
    public static RuntimeFeature parse(String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("pondererlib:")) normalized = normalized.substring(12);
        if (normalized.equals("java_steps")) return CORE;
        for (RuntimeFeature feature : values()) if (feature.key.equals(normalized)) return feature;
        throw new IllegalArgumentException("Unknown Ponderer Lib startup feature: " + key);
    }
}
