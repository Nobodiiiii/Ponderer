package com.nododiiiii.ponderer.runtime.api;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.runtime.core.PonderRuntimeEngine;
import com.nododiiiii.ponderer.runtime.core.ResourceAttribution;
import com.nododiiiii.ponderer.runtime.spi.*;
import com.nododiiiii.ponderer.scene.*;
import net.createmod.ponder.api.registration.*;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.registration.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Items;

import java.util.*;
import java.util.function.Consumer;

/** Resource-pack-aware content lifecycle shared by loader adapters. All calls run on the client thread. */
public final class ResourceSceneContents {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Consumer<RuntimeEngine>> EXTENSIONS = new LinkedHashMap<>();
    private static final List<Loaded> CONTENTS = new ArrayList<>();
    private static ResourceManager resources;
    private static boolean installed;
    private static boolean dirty;
    private static boolean refresh;

    private ResourceSceneContents() {}

    public static void install() {
        if (installed) return;
        installed = true;
        // Collect the built-in partial model before Flywheel bakes additional models.
        ResourceAttribution.initialize();
        PonderIndex.addPlugin(new ContentPlugin());
    }

    /** Optional compiled capabilities remain host-owned and are selected explicitly by manifest IDs. */
    public static AutoCloseable registerExtension(String id, Consumer<RuntimeEngine> installer) {
        ResourceLocation.parse(id);
        Objects.requireNonNull(installer);
        if (EXTENSIONS.putIfAbsent(id, installer) != null) throw new IllegalArgumentException("Duplicate content extension " + id);
        refresh = resources != null;
        return () -> { if (EXTENSIONS.remove(id, installer)) refresh = resources != null; };
    }

    public static void reload(ResourceManager manager) {
        resources = manager;
        CONTENTS.forEach(content -> content.engine.close());
        CONTENTS.clear();
        Set<String> sceneIds = new HashSet<>();
        Set<String> storyboardIds = new HashSet<>();
        // listResources applies normal pack precedence; sorting makes conflict diagnostics deterministic.
        var manifests = manager.listResources("ponderer/content", id -> id.getPath().endsWith(".json"));
        for (var entry : new TreeMap<>(manifests).entrySet()) {
            ResourceLocation manifestId = entry.getKey();
            RuntimeEngine engine = null;
            try (var reader = entry.getValue().openAsReader()) {
                ResourceContentManifest manifest = new Gson().fromJson(reader, ResourceContentManifest.class);
                if (manifest == null || manifest.formatVersion != 1 || manifest.scenes == null || manifest.extensions == null)
                    throw new IllegalArgumentException("Unsupported or incomplete content manifest");
                String namespace = manifestId.getNamespace();
                var source = new AssetStructureSource(namespace, () -> manager);
                engine = new PonderRuntimeEngine(namespace, source, IngredientResolver.NONE, RegistrationContribution.NONE, () -> false);
                for (String extension : manifest.extensions) {
                    var installer = EXTENSIONS.get(extension);
                    if (installer == null) throw new IllegalArgumentException("Missing compiled extension " + extension);
                    installer.accept(engine);
                }
                List<SceneSnapshot> documents = new ArrayList<>();
                Set<String> localIds = new HashSet<>();
                Set<String> localStoryboards = new HashSet<>();
                for (String sceneResource : manifest.scenes) {
                    try {
                        ResourceLocation resource = ResourceLocation.parse(sceneResource);
                        if (!resource.getPath().startsWith("ponderer/scenes/") || !resource.getPath().endsWith(".json"))
                            throw new IllegalArgumentException("Expected ponderer/scenes/*.json");
                        DslScene scene;
                        try (var input = manager.getResourceOrThrow(resource).openAsReader()) { scene = SceneCodec.decode(input); }
                        if (scene == null || scene.id == null || scene.scenes == null || scene.items == null || scene.tags == null || scene.customTags == null)
                            throw new IllegalArgumentException("Incomplete scene document");
                        scene.id = ResourceLocation.fromNamespaceAndPath(namespace, ResourceLocation.parse(scene.id).getPath()).toString();
                        scene.pack = null;
                        scene.editable = false;
                        SceneRequirements.require(scene, engine.steps());
                        if (scene.scenes.stream().anyMatch(segment -> segment == null || segment.steps == null))
                            throw new IllegalArgumentException("Null segment or steps");
                        for (int i = 0; i < scene.items.size(); i++) {
                            if (SceneItemBindings.id(scene.items.get(i)) == null) throw new IllegalArgumentException("Invalid item binding " + scene.items.get(i));
                            String filter = SceneItemBindings.nbtFilter(scene, i);
                            if (filter != null) net.minecraft.nbt.TagParser.parseTag(filter);
                        }
                        for (String tag : scene.tags) ResourceLocation.parse(tag);
                        for (var tag : scene.customTags.entrySet()) {
                            if (!ResourceLocation.parse(tag.getKey()).getNamespace().equals(namespace) || tag.getValue() == null)
                                throw new IllegalArgumentException("Custom tag must belong to " + namespace + ": " + tag.getKey());
                        }
                        Set<String> structures = new LinkedHashSet<>();
                        StructureReferences.collect(scene, structures);
                        for (String structure : structures) {
                            ResourceLocation id = structure.contains(":") ? ResourceLocation.parse(structure)
                                : ResourceLocation.fromNamespaceAndPath(namespace, structure);
                            source.loadSchematic(id);
                        }
                        Set<String> storyboards = new HashSet<>();
                        for (int i = 0; i < scene.scenes.size(); i++) {
                            var segment = scene.scenes.get(i);
                            if (segment.steps.isEmpty()) continue;
                            String suffix = segment.id == null || segment.id.isBlank() ? Integer.toString(i + 1) : segment.id;
                            String storyboard = ResourceLocation.parse(scene.id + (scene.scenes.size() > 1 ? "_" + suffix : "")).toString();
                            if (!storyboards.add(storyboard) || storyboardIds.contains(storyboard) || localStoryboards.contains(storyboard))
                                throw new IllegalArgumentException("Duplicate storyboard ID " + storyboard);
                        }
                        if (sceneIds.contains(scene.id) || localIds.contains(scene.id)) throw new IllegalArgumentException("Duplicate scene ID " + scene.id);
                        documents.add(SceneSnapshot.of(scene));
                        localIds.add(scene.id);
                        localStoryboards.addAll(storyboards);
                    } catch (Exception error) {
                        LOGGER.error("Ponderer Lib content {} scene {} unavailable: {}", manifestId, sceneResource, error.toString());
                    }
                }
                engine.replaceCatalog(() -> documents);
                CONTENTS.add(new Loaded(namespace, manifest.attribution, engine));
                sceneIds.addAll(localIds);
                storyboardIds.addAll(localStoryboards);
                engine = null;
                LOGGER.info("Ponderer Lib content {}: loaded {} scenes", manifestId, documents.size());
            } catch (Exception error) {
                LOGGER.error("Ponderer Lib content {} unavailable: {}", manifestId, error.toString());
            } finally {
                if (engine != null) engine.close();
            }
        }
        dirty = true;
        refresh = false;
    }

    /** Defer registry rebuilding until startup registration and the current resource reload have finished. */
    public static void tick() {
        if (refresh) reload(resources);
        if (!dirty) return;
        dirty = false;
        PonderIndex.reload();
        LOGGER.info("Ponderer Lib registered {} resource content manifests", CONTENTS.size());
    }

    public static void onItemTagsUpdated() {
        if (CONTENTS.stream().anyMatch(content -> content.engine.catalog().getScenes().stream().anyMatch(SceneItemBindings::hasTagBindings)))
            dirty = true;
    }

    private record Loaded(String namespace, boolean attribution, RuntimeEngine engine) {}

    private static final class ContentPlugin implements PonderPlugin {
        @Override public String getModId() { return "pondererlib"; }
        @Override public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> ignored) {
            for (Loaded content : CONTENTS) content.engine.plugin().registerScenes(new DefaultPonderSceneRegistrationHelper(
                content.namespace, (PonderSceneRegistry) PonderIndex.getSceneAccess()));
        }
        @Override public void registerTags(PonderTagRegistrationHelper<ResourceLocation> ignored) {
            Set<ResourceLocation> registered = new HashSet<>();
            for (Loaded content : CONTENTS) {
                var helper = new DefaultPonderTagRegistrationHelper(content.namespace,
                    (PonderTagRegistry) PonderIndex.getTagAccess(), (PonderLocalization) PonderIndex.getLangAccess());
                for (DslScene scene : content.engine.catalog().getScenes()) for (var entry : scene.customTags.entrySet()) {
                    ResourceLocation id = ResourceLocation.parse(entry.getKey());
                    if (!registered.add(id)) continue;
                    var tag = entry.getValue();
                    ResourceLocation icon = ResourceLocation.tryParse(tag.icon == null ? "minecraft:book" : tag.icon);
                    helper.registerTag(id).title(tag.title == null ? id.getPath() : tag.title.resolve())
                        .description(tag.description == null ? "" : tag.description.resolve())
                        .item(icon == null ? Items.BOOK : BuiltInRegistries.ITEM.getOptional(icon).orElse(Items.BOOK)).register();
                }
                if (content.attribution && registered.add(ResourceLocation.fromNamespaceAndPath(content.namespace, "ponderer_exported")))
                    ResourceAttribution.register(content.namespace, helper);
                content.engine.plugin().registerTags(helper);
            }
        }
        @Override public void registerSharedText(SharedTextRegistrationHelper ignored) {
            for (Loaded content : CONTENTS) content.engine.plugin().registerSharedText(new DefaultSharedTextRegistrationHelper(
                content.namespace, (PonderLocalization) PonderIndex.getLangAccess()));
        }
    }
}
