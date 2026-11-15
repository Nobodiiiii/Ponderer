package com.nododiiiii.ponderer.bootstrap.launch;

import com.nododiiiii.ponderer.runtime.api.RuntimeFeature;
import com.nododiiiii.ponderer.runtime.api.RuntimeFeatures;
import com.nododiiiii.ponderer.runtime.spi.StartupFeatureProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/** Product policy is resolved before Lib hooks freeze, regardless of plugin load order. */
public final class ProductFeatures implements StartupFeatureProvider {
    private static volatile Set<ProductFeature> requested;
    public ProductFeatures() {}

    @Override public Set<RuntimeFeature> requirements(Path gameDirectory, Properties legacyConfig, Set<String> requestedKeys) {
        Properties config = new Properties();
        Path path = Path.of(System.getProperty("ponderer.config", gameDirectory.resolve("config/ponderer.properties").toString()));
        if (Files.exists(path)) try (var input = Files.newInputStream(path)) { config.load(input); }
        catch (IOException error) { throw new IllegalStateException("Cannot read " + path, error); }
        EnumSet<ProductFeature> selected = EnumSet.noneOf(ProductFeature.class);
        for (ProductFeature feature : ProductFeature.values()) {
            String key = feature.key();
            String value = System.getProperty("ponderer." + key, config.getProperty(key,
                System.getProperty("pondererlib." + key, legacyConfig.getProperty(key, "AUTO"))));
            RuntimeFeatures.Policy policy;
            try { policy = RuntimeFeatures.Policy.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException error) { throw new IllegalArgumentException("Invalid ponderer " + key + " policy: " + value, error); }
            // The installed product requests all its modules by default; OFF wins over old feature lists.
            if (policy != RuntimeFeatures.Policy.OFF) selected.add(feature);
        }
        if (!selected.contains(ProductFeature.APPLICATION)) selected.remove(ProductFeature.EDITOR);
        requested = Set.copyOf(selected);
        EnumSet<RuntimeFeature> requirements = EnumSet.noneOf(RuntimeFeature.class);
        selected.forEach(feature -> requirements.addAll(feature.requirements()));
        System.getLogger("Ponderer/Mixins").log(System.Logger.Level.INFO, "Product features requested={0}", selected);
        return requirements;
    }

    @Override public boolean ownsFeatureKey(String key) {
        for (ProductFeature feature : ProductFeature.values()) if (feature.key().equals(key)) return true;
        return false;
    }
    public static boolean enabled(ProductFeature feature) {
        if (requested == null) throw new IllegalStateException("Ponderer startup policy has not been initialized");
        return requested.contains(feature) && feature.requirements().stream().allMatch(RuntimeFeatures::enabled)
            && (feature != ProductFeature.EDITOR || enabled(ProductFeature.APPLICATION));
    }
    public static void require(ProductFeature feature, String location) {
        if (!enabled(feature)) throw new IllegalStateException(location + ": ponderer." + feature.key()
            + " or its Lib prerequisites are disabled; enable them and restart");
    }
}
