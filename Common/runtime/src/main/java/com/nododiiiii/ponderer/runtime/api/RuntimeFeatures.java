package com.nododiiiii.ponderer.runtime.api;

import com.nododiiiii.ponderer.runtime.launch.RuntimeFeatureDiscovery;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Frozen before transformation; runtime requests never install late Mixins. */
public final class RuntimeFeatures {
    public enum Policy { AUTO, ON, OFF }
    private record State(Set<RuntimeFeature> requested, Set<RuntimeFeature> enabled,
                         EnumMap<RuntimeFeature, Policy> policies) {}
    private static volatile State startup;
    private static final Set<Object> PLAYER_REQUESTS = ConcurrentHashMap.newKeySet();
    private RuntimeFeatures() {}

    /** Loader plugins supply actual mod roots, including nested jars and development roots. */
    public static synchronized void initialize(Collection<Path> roots, Path gameDirectory) {
        if (startup != null) return;
        Properties config = new Properties();
        Path path = Path.of(System.getProperty("pondererlib.config",
            gameDirectory.resolve("config/pondererlib.properties").toString()));
        if (Files.exists(path)) try (var input = Files.newInputStream(path)) { config.load(input); }
        catch (IOException error) { throw new IllegalStateException("Cannot read " + path, error); }
        EnumSet<RuntimeFeature> requested = RuntimeFeatureDiscovery.discover(roots, gameDirectory);
        String extra = System.getProperty("pondererlib.features", config.getProperty("features", ""));
        for (String key : extra.split("[,\\s]+")) if (!key.isBlank()) requested.add(RuntimeFeature.parse(key));
        EnumMap<RuntimeFeature, Policy> policies = new EnumMap<>(RuntimeFeature.class);
        for (RuntimeFeature feature : RuntimeFeature.values()) {
            String value = System.getProperty("pondererlib." + feature.key(), config.getProperty(feature.key(), "AUTO"));
            Policy policy;
            try { policy = Policy.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException error) { throw new IllegalArgumentException("Invalid pondererlib " + feature.key() + " policy: " + value, error); }
            policies.put(feature, policy);
            if (policy == Policy.ON) requested.add(feature);
        }
        EnumSet<RuntimeFeature> enabled = requested.clone();
        boolean changed;
        do {
            changed = false;
            for (RuntimeFeature feature : EnumSet.copyOf(enabled)) changed |= enabled.addAll(feature.dependencies());
        } while (changed);
        enabled.removeIf(feature -> policies.get(feature) == Policy.OFF);
        do { changed = enabled.removeIf(feature -> !enabled.containsAll(feature.dependencies())); }
        while (changed);
        startup = new State(Set.copyOf(requested), Set.copyOf(enabled), policies);
        System.getLogger("PondererLib/Mixins").log(System.Logger.Level.INFO,
            "Startup features requested={0}, enabled={1}", requested, enabled);
    }
    private static State state() {
        State value = startup;
        if (value == null) throw new IllegalStateException("Ponderer Lib startup features have not been initialized by the loader Mixin plugin");
        return value;
    }
    public static boolean enabled(RuntimeFeature feature) { return state().enabled.contains(feature); }
    public static Set<RuntimeFeature> enabledFeatures() { return state().enabled; }
    public static Policy policy(RuntimeFeature feature) { return state().policies.get(feature); }
    public static void require(RuntimeFeature feature, String location) {
        if (!enabled(feature)) throw new IllegalStateException(location + ": capability pondererlib:" + feature.key()
            + " is unavailable at startup. Declare it in META-INF/pondererlib.features.properties (features="
            + feature.key() + ") or set -Dpondererlib." + feature.key()
            + "=ON and restart; OFF prerequisites must also be enabled.");
    }

    /** Declarations install hooks; independently owned requests activate UI behavior. */
    public static AutoCloseable requestPlayerEnhancements(String owner) {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("Missing request owner");
        if (!enabled(RuntimeFeature.PLAYER)) {
            if (playerPolicy() == Policy.OFF || state().requested.contains(RuntimeFeature.PLAYER)) return () -> {};
            require(RuntimeFeature.PLAYER, owner);
        }
        Object token = new Object();
        PLAYER_REQUESTS.add(token);
        return () -> PLAYER_REQUESTS.remove(token);
    }
    public static boolean playerEnabled() {
        return enabled(RuntimeFeature.PLAYER) && (playerPolicy() == Policy.ON || !PLAYER_REQUESTS.isEmpty());
    }
    public static boolean interfacesEnabled() { return enabled(RuntimeFeature.INTERFACES); }
    public static void requireInterfaces(String location) {
        require(RuntimeFeature.INTERFACES, location);
    }
    public static Policy playerPolicy() { return policy(RuntimeFeature.PLAYER); }
    public static Policy interfacePolicy() { return policy(RuntimeFeature.INTERFACES); }
}
