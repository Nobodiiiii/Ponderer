package com.nododiiiii.ponderer.features.projector;

import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.runtime.api.SceneItemBindings;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ProjectorSceneResolver {

    private ProjectorSceneResolver() {
    }

    public static List<String> sceneKeysFor(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.is(Items.AIR)) {
            return List.of();
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null) {
            return List.of();
        }

        Set<String> result = new LinkedHashSet<>();
        for (DslScene scene : com.nododiiiii.ponderer.application.api.RuntimeAccess.engine().catalog().getScenes()) {
            if (scene == null || scene.id == null || !SceneItemBindings.matchesWithNbt(scene, stack)) {
                continue;
            }
            result.add(scene.sceneKey());
        }
        return new ArrayList<>(result);
    }

}
