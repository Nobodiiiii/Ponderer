package com.nododiiiii.ponderer.runtime.core;

import com.nododiiiii.ponderer.runtime.core.NbtSceneFilter;
import com.nododiiiii.ponderer.runtime.core.SceneItemBindings;
import com.nododiiiii.ponderer.scene.DslScene;
import com.mojang.logging.LogUtils;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import java.util.ArrayList;
import java.util.List;
import com.nododiiiii.ponderer.runtime.api.SceneCatalog;
import com.nododiiiii.ponderer.runtime.spi.RegistrationContribution;

/** Ponder registration adapter; compilation and step execution have separate owners. */
public final class DynamicPonderPlugin implements PonderPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final SceneCatalog catalog;
    private final SceneCompiler compiler;
    private final RegistrationContribution contribution;
    public DynamicPonderPlugin(SceneCatalog catalog, SceneCompiler compiler, RegistrationContribution contribution) {
        this.catalog = catalog;
        this.compiler = compiler;
        this.contribution = contribution;
    }
    @Override public String getModId() { return compiler.getModId(); }

    private void registerScene(PonderSceneRegistrationHelper<ResourceLocation> helper, DslScene scene) {
        if (scene.items == null || scene.items.isEmpty()) {
            LOGGER.warn("Scene {} has no items; skipping", scene.id);
            return;
        }

        List<ResourceLocation> components = SceneItemBindings.resolve(scene.items);
        List<SceneItemBindings.ResolvedBinding> bindings = SceneItemBindings.resolveWithNbt(scene);

        if (components.isEmpty()) {
            LOGGER.warn("Scene {} has no valid items; skipping", scene.id);
            return;
        }

        ResourceLocation[] tags = resolveTags(scene.tags);

        List<DslScene.SceneSegment> sceneList = compiler.normalizeScenes(scene);
        List<ResourceLocation> schematics = compiler.resolveSceneSchematics(scene, sceneList);
        var multi = helper.forComponents(components);
        for (int i = 0; i < sceneList.size(); i++) {
            DslScene.SceneSegment sc = sceneList.get(i);
            if (sc.steps == null || sc.steps.isEmpty()) {
                continue;
            }
            ResourceLocation schematic = schematics.get(i);
            multi.addStoryBoard(schematic, compiler.createStoryBoard(scene, sc, i, sceneList.size()), tags);

            // Register scene in NbtSceneFilter
            ResourceLocation baseId = ResourceLocation.tryParse(scene.id);
            String basePath = baseId == null ? "scene" : baseId.getPath();
            String scenePath = sceneList.size() > 1 ? basePath + "_" + compiler.sceneSuffix(sc, i) : basePath;
            String fullSceneId = compiler.getModId() + ":" + scenePath;
            for (SceneItemBindings.ResolvedBinding binding : bindings) {
                NbtSceneFilter.registerScene(binding.itemId(), fullSceneId,
                    NbtSceneFilter.parseNbt(binding.nbtFilter()));
            }

            // Register mapping from PonderScene ID to DslScene sceneKey for pack disambiguation
            catalog.registerPonderIdMapping(fullSceneId, scene.sceneKey(), i);
        }
    }

    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        contribution.beforeTags(helper);
        for (DslScene scene : catalog.getScenes()) {
            ResourceLocation[] tags = resolveTags(scene.tags);
            for (ResourceLocation item : SceneItemBindings.resolve(scene.items)) {
                for (ResourceLocation tag : tags) {
                    helper.addTagToComponent(item, tag);
                }
            }
        }
        contribution.afterTags(helper);
    }

    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        NbtSceneFilter.clear();
        catalog.clearPonderIdMapping();
        for (DslScene scene : catalog.getScenes()) {
            registerScene(helper, scene);
        }
        contribution.afterScenes(helper);
    }

    private ResourceLocation[] resolveTags(List<String> tags) {
        List<ResourceLocation> result = new ArrayList<>();
        for (String tagId : contribution.tagsFor(tags)) {
            ResourceLocation tag = ResourceLocation.tryParse(tagId);
            if (tag != null) {
                result.add(tag);
            }
        }
        return result.toArray(ResourceLocation[]::new);
    }

    public void registerSharedText(SharedTextRegistrationHelper helper) {
        for (DslScene scene : catalog.getScenes()) {
            List<DslScene.SceneSegment> sceneList = compiler.normalizeScenes(scene);
            for (DslScene.SceneSegment sc : sceneList) {
                if (sc.steps == null) {
                    continue;
                }
                for (DslScene.DslStep step : sc.steps) {
                    if (step == null || step.type == null) {
                        continue;
                    }
                    if (!"shared_text".equalsIgnoreCase(step.type)) {
                        continue;
                    }
                    if (step.key == null || step.key.isBlank() || step.text == null || step.text.resolve().isBlank()) {
                        continue;
                    }
                    helper.registerSharedText(step.key, step.text.resolve());
                }
            }
        }
    }
}
