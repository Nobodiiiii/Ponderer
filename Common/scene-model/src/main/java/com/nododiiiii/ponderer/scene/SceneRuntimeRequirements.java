package com.nododiiiii.ponderer.scene;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Shared startup discovery, runtime validation and export rules; no game classes. */
public final class SceneRuntimeRequirements {
    private SceneRuntimeRequirements() {}
    public static Set<String> features(DslScene scene) {
        Set<String> features = new LinkedHashSet<>(Set.of("core", "render_cache"));
        if (scene == null) return features;
        if (scene.nbtFilter != null && !scene.nbtFilter.isBlank()
            || scene.itemNbtFilters != null && scene.itemNbtFilters.stream().anyMatch(value -> value != null && !value.isBlank())) features.add("nbt_bindings");
        if (scene.requiredCapabilities != null) for (String capability : scene.requiredCapabilities) {
            if (capability == null || !capability.startsWith("pondererlib:")) continue;
            String key = capability.substring(12);
            if (Set.of("core", "view", "text", "interfaces", "nbt_bindings", "player", "timeline", "shader_lights", "render_cache", "detached_rendering", "ui_extensions").contains(key)) features.add(key);
        }
        if (scene.scenes != null) for (var segment : scene.scenes) {
            if (segment == null || segment.steps == null) continue;
            for (var step : segment.steps) collectStepFeatures(step, features);
        }
        return Set.copyOf(features);
    }
    public static Set<String> stepFeatures(String type) {
        String key = type.toLowerCase(Locale.ROOT);
        if (SceneStepTypes.INTERFACES.contains(key)) return Set.of("interfaces");
        if (key.equals("zoom_scene")) return Set.of("view");
        if (key.equals("text") || key.equals("shared_text") || key.equals("show_controls")) return Set.of("text");
        return Set.of();
    }
    private static void collectStepFeatures(DslScene.DslStep step, Set<String> features) {
        if (step == null || step.type == null) return;
        features.addAll(stepFeatures(step.type));
        if ("interface_panel".equalsIgnoreCase(step.type) && step.steps != null)
            for (var child : step.steps) collectStepFeatures(child, features);
    }
}
