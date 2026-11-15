package com.nododiiiii.ponderer.runtime.launch;

import com.google.gson.JsonParser;
import com.nododiiiii.ponderer.runtime.api.RuntimeFeature;
import com.nododiiiii.ponderer.scene.SceneCodec;
import com.nododiiiii.ponderer.scene.SceneRuntimeRequirements;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;

/** Data discovery plus explicitly registered, game-independent startup providers. */
public final class RuntimeFeatureDiscovery {
    private RuntimeFeatureDiscovery() {}

    /** Only installed mods can explicitly opt into compiled startup policy. */
    public static java.util.List<com.nododiiiii.ponderer.runtime.spi.StartupFeatureProvider> providers(Collection<Path> roots) {
        var providers = new java.util.LinkedHashMap<String, com.nododiiiii.ponderer.runtime.spi.StartupFeatureProvider>();
        for (Path root : roots) {
            Path path = root.resolve("META-INF/pondererlib.startup.properties");
            if (!Files.isRegularFile(path)) continue;
            Properties declaration = new Properties();
            try (var input = Files.newInputStream(path)) { declaration.load(input); }
            catch (IOException error) { throw new IllegalStateException("Cannot read " + path, error); }
            String name = declaration.getProperty("provider", "").trim();
            if (providers.containsKey(name)) continue;
            try {
                var type = Class.forName(name, true, RuntimeFeatureDiscovery.class.getClassLoader())
                    .asSubclass(com.nododiiiii.ponderer.runtime.spi.StartupFeatureProvider.class);
                providers.put(name, type.getConstructor().newInstance());
            } catch (ReflectiveOperationException | ClassCastException error) {
                throw new IllegalStateException("Invalid startup provider in " + path + ": " + name, error);
            }
        }
        return java.util.List.copyOf(providers.values());
    }
    public static EnumSet<RuntimeFeature> discover(Collection<Path> roots, Path gameDirectory) {
        EnumSet<RuntimeFeature> features = EnumSet.noneOf(RuntimeFeature.class);
        Set<String> references = new java.util.LinkedHashSet<>();
        var modRoots = new java.util.LinkedHashSet<>(roots);
        var selectedPacks = new java.util.ArrayList<Path>();
        Path options = gameDirectory.resolve("options.txt");
        if (Files.isRegularFile(options)) try (var lines = Files.lines(options)) {
            var selected = lines.filter(line -> line.startsWith("resourcePacks:")).findFirst();
            if (selected.isPresent()) for (var entry : JsonParser.parseString(selected.get().substring("resourcePacks:".length())).getAsJsonArray()) {
                String name = entry.getAsString();
                if (!name.startsWith("file/")) continue;
                Path packs = gameDirectory.resolve("resourcepacks").toAbsolutePath().normalize();
                Path pack = packs.resolve(name.substring(5)).normalize();
                if (!pack.startsWith(packs)) throw new IllegalArgumentException("Resource pack escapes resourcepacks: " + name);
                if (Files.exists(pack)) selectedPacks.add(pack);
            }
        } catch (IOException | RuntimeException error) {
            warn("Cannot inspect selected packs from " + options, error);
        }
        for (Path root : modRoots) readRoot(root, features, references);
        for (Path pack : selectedPacks) withPack(pack, root -> readRoot(root, features, references));
        // References and data can belong to different mods/packs. Inspect every contribution to a
        // referenced resource so overrides and cross-pack manifests cannot omit required hooks.
        for (Path root : modRoots) readReferencedScenes(root, references, features);
        for (Path pack : selectedPacks) withPack(pack, root -> readReferencedScenes(root, references, features));
        return features;
    }
    private static void withPack(Path pack, java.util.function.Consumer<Path> reader) {
        if (Files.isDirectory(pack)) reader.accept(pack);
        else try (var zip = FileSystems.newFileSystem(pack)) { reader.accept(zip.getPath("/")); }
        catch (IOException error) { warn("Cannot inspect selected pack " + pack, error); }
    }
    private static void readRoot(Path root, EnumSet<RuntimeFeature> features, Set<String> references) {
        readDeclaration(root.resolve("META-INF/pondererlib.features.properties"), features);
        Path declarations = root.resolve("META-INF/pondererlib.features");
        if (Files.isDirectory(declarations)) try (var files = Files.list(declarations)) {
            files.filter(path -> path.getFileName().toString().endsWith(".properties")).sorted().forEach(path -> readDeclaration(path, features));
        } catch (IOException error) { throw new IllegalStateException("Cannot read " + declarations, error); }
        // Only content-manifest references are inspected; unrelated JSON is never scanned.
        Path assets = root.resolve("assets");
        if (!Files.isDirectory(assets)) return;
        try (var namespaces = Files.list(assets)) {
            for (Path namespace : namespaces.toList()) {
                Path content = namespace.resolve("ponderer/content");
                if (!Files.isDirectory(content)) continue;
                try (var manifests = Files.list(content)) {
                    for (Path manifest : manifests.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                        try (var input = Files.newBufferedReader(manifest)) {
                            var json = JsonParser.parseReader(input).getAsJsonObject();
                            if (!json.has("scenes")) continue;
                            if (json.has("formatVersion") && json.get("formatVersion").getAsInt() != 1) continue;
                            features.add(RuntimeFeature.CORE);
                            features.add(RuntimeFeature.RENDER_CACHE);
                            for (var id : json.getAsJsonArray("scenes")) {
                                String resource = id.getAsString();
                                if (!resource.matches("[a-z0-9_.-]+:ponderer/scenes/[a-z0-9/._-]+\\.json"))
                                    throw new IllegalArgumentException("Invalid scene resource " + id);
                                references.add(resource);
                            }
                        } catch (IOException | RuntimeException error) {
                            warn("Cannot inspect content " + manifest, error);
                        }
                    }
                }
            }
        } catch (IOException error) { throw new IllegalStateException("Cannot read content declarations in " + assets, error); }
    }
    private static void readReferencedScenes(Path root, Set<String> references, EnumSet<RuntimeFeature> features) {
        Path assets = root.resolve("assets").normalize();
        for (String reference : references) {
            String[] parts = reference.split(":", 2);
            Path document = assets.resolve(parts[0]).resolve(parts[1]).normalize();
            if (!document.startsWith(assets) || !Files.isRegularFile(document)) continue;
            try (var reader = Files.newBufferedReader(document)) {
                for (String feature : SceneRuntimeRequirements.features(SceneCodec.decode(reader))) features.add(RuntimeFeature.parse(feature));
            } catch (IOException | RuntimeException error) { warn("Cannot inspect scene " + document, error); }
        }
    }
    private static void warn(String message, Exception error) {
        System.getLogger("PondererLib/Mixins").log(System.Logger.Level.WARNING, message + ": " + error);
    }
    private static void readDeclaration(Path path, EnumSet<RuntimeFeature> features) {
        if (!Files.isRegularFile(path)) return;
        Properties declaration = new Properties();
        try (var input = Files.newInputStream(path)) { declaration.load(input); }
        catch (IOException error) { throw new IllegalStateException("Cannot read " + path, error); }
        for (String key : declaration.getProperty("features", "").split("[,\\s]+"))
            if (!key.isBlank()) features.add(RuntimeFeature.parse(key));
    }
}
