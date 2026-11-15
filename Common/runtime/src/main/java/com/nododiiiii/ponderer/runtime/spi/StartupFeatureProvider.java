package com.nododiiiii.ponderer.runtime.spi;

import com.nododiiiii.ponderer.runtime.api.RuntimeFeature;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

/**
 * Explicit mod-owned startup policy, invoked before any Lib Mixin decision.
 * Implementations must only use JDK and startup API types: game classes are not ready.
 * Register in META-INF/pondererlib.startup.properties with provider=<class name>.
 * Resource packs cannot register providers.
 */
public interface StartupFeatureProvider {
    Set<RuntimeFeature> requirements(Path gameDirectory, Properties runtimeConfig, Set<String> requestedKeys);

    /** Legacy names accepted from the global features list; their policy stays with the host. */
    default boolean ownsFeatureKey(String key) { return false; }
}
