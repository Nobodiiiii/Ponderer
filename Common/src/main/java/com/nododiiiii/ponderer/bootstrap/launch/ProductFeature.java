package com.nododiiiii.ponderer.bootstrap.launch;

import com.nododiiiii.ponderer.runtime.api.RuntimeFeature;
import java.util.Set;

/** Ponderer product modules; never part of the standalone Lib capability set. */
public enum ProductFeature {
    APPLICATION("application", RuntimeFeature.CORE, RuntimeFeature.SHADER_LIGHTS),
    EDITOR("editor", RuntimeFeature.DETACHED_RENDERING, RuntimeFeature.UI_EXTENSIONS, RuntimeFeature.TIMELINE),
    FEEDBACK("feedback");

    private final String key;
    private final Set<RuntimeFeature> requirements;
    ProductFeature(String key, RuntimeFeature... requirements) {
        this.key = key;
        this.requirements = Set.of(requirements);
    }
    public String key() { return key; }
    public Set<RuntimeFeature> requirements() { return requirements; }
}
