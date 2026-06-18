package com.nododiiiii.ponderer.projector.client;

import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import com.nododiiiii.ponderer.projector.ProjectorSceneKey;
import com.nododiiiii.ponderer.projector.ProjectorSceneResolver;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProjectorClientSceneResolver {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ProjectorClientSceneResolver() {
    }

    public static List<String> sceneKeysFor(ItemStack stack) {
        Set<String> result = new LinkedHashSet<>(ProjectorSceneResolver.sceneKeysFor(stack));
        if (stack == null || stack.isEmpty() || stack.is(Items.AIR)) {
            return new ArrayList<>(result);
        }

        ResourceLocation componentId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (componentId == null || !PonderIndex.getSceneAccess().doScenesExistForId(componentId)) {
            return new ArrayList<>(result);
        }

        List<PonderScene> scenes;
        try {
            scenes = ProjectorRenderContext.supply(() -> PonderIndex.getSceneAccess().compile(componentId));
        } catch (Throwable t) {
            LOGGER.debug("Skipping native projector scene discovery for {}", componentId, t);
            return new ArrayList<>(result);
        }

        Map<ResourceLocation, Integer> occurrences = new HashMap<>();
        for (PonderScene scene : scenes) {
            if (scene == null || scene.getId() == null) {
                continue;
            }
            ResourceLocation sceneId = scene.getId();
            int occurrence = occurrences.getOrDefault(sceneId, 0);
            occurrences.put(sceneId, occurrence + 1);

            if (SceneRuntime.findBySceneId(sceneId) != null) {
                continue;
            }

            result.add(ProjectorSceneKey.nativeKey(componentId, sceneId, occurrence));
        }

        return new ArrayList<>(result);
    }
}
