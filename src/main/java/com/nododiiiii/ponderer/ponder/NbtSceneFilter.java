package com.nododiiiii.ponderer.ponder;

import com.mojang.logging.LogUtils;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for NBT-based ponder scene filtering.
 * Scenes can have an optional NBT filter; when present, the scene is only shown
 * if the player's hovered/held item matches the filter pattern.
 */
public final class NbtSceneFilter {
    private static final Logger LOGGER = LogUtils.getLogger();

    // sceneId (e.g. "ponderer:ponderer_example") -> required NBT pattern
    private static final Map<String, CompoundTag> FILTERS = new ConcurrentHashMap<>();

    // itemId -> set of scene IDs registered by our plugin (both filtered and unfiltered)
    private static final Map<ResourceLocation, Set<String>> ALL_SCENES = new ConcurrentHashMap<>();

    // ThreadLocal to pass the hovered ItemStack from PonderUI.of() into compile()
    static final ThreadLocal<ItemStack> CURRENT_STACK = new ThreadLocal<>();

    private NbtSceneFilter() {}

    // ---- Registration ----

    /**
     * Register a scene for an item. Called for ALL scenes (with or without NBT filter).
     */
    public static void registerScene(ResourceLocation itemId, String sceneId) {
        ALL_SCENES.computeIfAbsent(itemId, k -> ConcurrentHashMap.newKeySet()).add(sceneId);
    }

    /**
     * Register an NBT filter for a specific scene.
     */
    public static void registerFilter(String sceneId, CompoundTag nbtFilter) {
        FILTERS.put(sceneId, nbtFilter);
    }

    /**
     * Clear all registrations (called on reload).
     */
    public static void clear() {
        FILTERS.clear();
        ALL_SCENES.clear();
    }

    // ---- ThreadLocal stack for mixin ----

    public static void setCurrentStack(ItemStack stack) {
        CURRENT_STACK.set(stack);
    }

    @Nullable
    public static ItemStack getCurrentStack() {
        return CURRENT_STACK.get();
    }

    public static void clearCurrentStack() {
        CURRENT_STACK.remove();
    }

    // ---- Filtering ----

    /**
     * Check whether any registered filters exist for the given item.
     */
    public static boolean hasFilters(ResourceLocation itemId) {
        Set<String> sceneIds = ALL_SCENES.get(itemId);
        if (sceneIds == null || sceneIds.isEmpty()) return false;
        for (String sid : sceneIds) {
            if (FILTERS.containsKey(sid)) return true;
        }
        return false;
    }

    /**
     * Check whether at least one scene is visible for the given ItemStack.
     * If no filters are registered for this item, returns true (pass through).
     * If filters exist, returns true if at least one scene has no filter OR matches.
     */
    public static boolean hasVisibleScenes(ItemStack stack, ResourceLocation itemId) {
        Set<String> sceneIds = ALL_SCENES.get(itemId);
        if (sceneIds == null || sceneIds.isEmpty()) return true;

        for (String sid : sceneIds) {
            CompoundTag filter = FILTERS.get(sid);
            if (filter == null) {
                // Unfiltered scene -> always visible
                return true;
            }
            if (matchesNbt(stack, filter)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Filter a compiled scene list based on the current ItemStack.
     * Scenes without an NBT filter are always included.
     * Scenes with an NBT filter are only included if the stack matches.
     * If ALL scenes would be filtered out, returns the original list as fallback.
     */
    public static List<PonderScene> filter(ItemStack stack, List<PonderScene> scenes) {
        if (FILTERS.isEmpty()) return scenes;

        List<PonderScene> filtered = new ArrayList<>();
        boolean anyFiltered = false;

        for (PonderScene scene : scenes) {
            String sceneId = scene.getId().toString();
            CompoundTag filter = FILTERS.get(sceneId);
            if (filter == null) {
                filtered.add(scene);
            } else {
                anyFiltered = true;
                if (matchesNbt(stack, filter)) {
                    filtered.add(scene);
                }
            }
        }

        if (!anyFiltered) return scenes;
        return filtered.isEmpty() ? scenes : filtered;
    }

    /**
     * Check if the ItemStack's serialized NBT contains all tags in the filter.
     * Uses subset matching: every key/value in filter must exist in the stack's tag.
     */
    public static boolean matchesNbt(ItemStack stack, CompoundTag filter) {
        if (filter == null || filter.isEmpty()) return true;

        try {
            CompoundTag stackTag = stack.save(new CompoundTag());

            return isSubset(filter, stackTag);
        } catch (Exception e) {
            LOGGER.debug("NBT match check failed", e);
            return false;
        }
    }

    /**
     * Recursive subset check: every key in subset must exist in superset with the same value.
     * For nested CompoundTags, recurse. For other tag types, use equals().
     */
    private static boolean isSubset(CompoundTag subset, CompoundTag superset) {
        for (String key : subset.getAllKeys()) {
            Tag subVal = subset.get(key);
            Tag superVal = superset.get(key);
            if (superVal == null) return false;

            if (subVal instanceof CompoundTag subCompound && superVal instanceof CompoundTag superCompound) {
                if (!isSubset(subCompound, superCompound)) return false;
            } else {
                if (!subVal.equals(superVal)) return false;
            }
        }
        return true;
    }

    /**
     * Parse an SNBT string into a CompoundTag, or null if invalid.
     */
    @Nullable
    public static CompoundTag parseNbt(String snbt) {
        if (snbt == null || snbt.isBlank()) return null;
        try {
            return TagParser.parseTag(snbt);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse NBT filter: {}", snbt, e);
            return null;
        }
    }

    @Nullable
    private static RegistryAccess getRegistryAccess() {
        var mc = Minecraft.getInstance();
        if (mc.level != null) return mc.level.registryAccess();
        return null;
    }
}
