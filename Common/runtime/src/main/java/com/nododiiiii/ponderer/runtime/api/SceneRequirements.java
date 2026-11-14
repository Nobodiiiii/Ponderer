package com.nododiiiii.ponderer.runtime.api;

import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.scene.SceneStepTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Validation happens before registering or executing a partial storyboard. */
public final class SceneRequirements {
    public static final int FORMAT_VERSION = 1;
    private SceneRequirements() {}
    public static List<String> diagnostics(DslScene scene, StepRegistry steps) {
        List<String> result = new ArrayList<>();
        for (String key : com.nododiiiii.ponderer.scene.SceneRuntimeRequirements.features(scene)) {
            // Cache reclamation is optional; it does not affect the meaning of scene instructions.
            if (key.equals("render_cache")) continue;
            if (!RuntimeFeatures.enabled(RuntimeFeature.parse(key))) result.add(scene.id + ": capability pondererlib:" + key
                + " unavailable at startup; declare the feature or set pondererlib." + key + "=ON and restart");
        }
        if (scene.formatVersion != FORMAT_VERSION) result.add(scene.id + ": unsupported format version " + scene.formatVersion + " (supported: " + FORMAT_VERSION + ")");
        if (scene.requiredCapabilities != null) for (String capability : scene.requiredCapabilities) {
            try {
                if (capability == null || !capability.startsWith("pondererlib:")) throw new IllegalArgumentException();
                if (!RuntimeFeatures.enabled(RuntimeFeature.parse(capability))) result.add(scene.id + ": missing capability " + capability);
            } catch (IllegalArgumentException error) {
                if (!steps.providesCapability(capability)) result.add(scene.id + ": missing capability " + capability);
            }
        }
        if (scene.scenes != null) for (int s = 0; s < scene.scenes.size(); s++) {
            var segment = scene.scenes.get(s);
            if (segment == null || segment.steps == null) continue;
            Set<String> anchors = new java.util.HashSet<>();
            for (int i = 0; i < segment.steps.size(); i++) {
                var step = segment.steps.get(i);
                if (step == null || step.type == null) continue;
                String location = scene.id + "/" + (segment.id == null ? s : segment.id) + "/" + (step.id == null ? i : step.id);
                if (step.id != null && !anchors.add(step.id)) result.add(location + ": duplicate step anchor");
                if (!SceneStepTypes.BUILTIN.contains(step.type.toLowerCase(java.util.Locale.ROOT)) && !steps.contains(step.type)) result.add(location + ": missing step provider " + step.type);
                if (SceneStepTypes.INTERFACES.contains(step.type.toLowerCase(java.util.Locale.ROOT)) && !RuntimeFeatures.interfacesEnabled()) result.add(location + ": capability pondererlib:interfaces is disabled at startup");
            }
        }
        return List.copyOf(result);
    }
    public static void require(DslScene scene, StepRegistry steps) {
        List<String> errors = diagnostics(scene, steps);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
    }
}
