package com.nododiiiii.ponderer.ponder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resolves carrier item IDs and #item_tag bindings without expanding the saved scene. */
public final class SceneItemBindings {
    public record ResolvedBinding(ResourceLocation itemId, @Nullable String nbtFilter) {
    }

    private SceneItemBindings() {
    }

    public static boolean isTag(@Nullable String binding) {
        return binding != null && binding.trim().startsWith("#");
    }

    @Nullable
    public static ResourceLocation id(@Nullable String binding) {
        if (binding == null || binding.isBlank()) return null;
        String value = binding.trim();
        return ResourceLocation.tryParse(isTag(value) ? value.substring(1) : value);
    }

    public static List<ResourceLocation> resolve(@Nullable List<String> bindings) {
        Set<ResourceLocation> items = new LinkedHashSet<>();
        if (bindings == null) return List.of();
        for (String binding : bindings) {
            ResourceLocation id = id(binding);
            if (id == null) continue;
            if (isTag(binding)) {
                BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id)).ifPresent(holders -> {
                    for (var holder : holders) {
                        if (holder.value() != Items.AIR) {
                            items.add(BuiltInRegistries.ITEM.getKey(holder.value()));
                        }
                    }
                });
            } else if (BuiltInRegistries.ITEM.getOptional(id).filter(item -> item != Items.AIR).isPresent()) {
                items.add(id);
            }
        }
        return List.copyOf(items);
    }

    @Nullable
    public static ResourceLocation firstItem(@Nullable DslScene scene) {
        List<ResourceLocation> items = resolve(scene == null ? null : scene.items);
        return items.isEmpty() ? null : items.get(0);
    }

    @Nullable
    public static String nbtFilter(DslScene scene, int bindingIndex) {
        String filter = scene.itemNbtFilters != null && bindingIndex < scene.itemNbtFilters.size()
            ? scene.itemNbtFilters.get(bindingIndex) : scene.nbtFilter;
        return filter == null || filter.isBlank() ? null : filter;
    }

    public static List<ResolvedBinding> resolveWithNbt(@Nullable DslScene scene) {
        Set<ResolvedBinding> bindings = new LinkedHashSet<>();
        if (scene == null || scene.items == null) return List.of();
        for (int i = 0; i < scene.items.size(); i++) {
            String binding = scene.items.get(i);
            if (id(binding) == null) continue;
            String filter = nbtFilter(scene, i);
            for (ResourceLocation itemId : resolve(List.of(binding))) {
                bindings.add(new ResolvedBinding(itemId, filter));
            }
        }
        return List.copyOf(bindings);
    }

    @Nullable
    public static String firstNbtFilter(DslScene scene) {
        // The first binding may be an empty tag, so use the first resolved carrier.
        List<ResolvedBinding> bindings = resolveWithNbt(scene);
        return bindings.isEmpty() ? null : bindings.get(0).nbtFilter();
    }

    public static boolean hasNbtFilters(DslScene scene) {
        if (scene.items == null) return false;
        for (int i = 0; i < scene.items.size(); i++) {
            if (nbtFilter(scene, i) != null) return true;
        }
        return false;
    }

    public static boolean matchesWithNbt(DslScene scene, ItemStack stack) {
        if (scene.items == null || stack.isEmpty()) return false;
        for (int i = 0; i < scene.items.size(); i++) {
            String binding = scene.items.get(i);
            if (id(binding) == null || !matches(List.of(binding), stack)) continue;
            String filter = nbtFilter(scene, i);
            if (filter == null) return true;
            var parsed = NbtSceneFilter.parseNbt(filter);
            if (parsed != null && NbtSceneFilter.matchesNbt(stack, parsed)) return true;
        }
        return false;
    }

    public static boolean matches(@Nullable List<String> bindings, ItemStack stack) {
        if (bindings == null || stack.isEmpty()) return false;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (String binding : bindings) {
            ResourceLocation id = id(binding);
            if (id == null) continue;
            if (isTag(binding) ? stack.is(TagKey.create(Registries.ITEM, id)) : itemId.equals(id)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasTagBindings(DslScene scene) {
        return scene.items != null && scene.items.stream().anyMatch(SceneItemBindings::isTag);
    }
}
