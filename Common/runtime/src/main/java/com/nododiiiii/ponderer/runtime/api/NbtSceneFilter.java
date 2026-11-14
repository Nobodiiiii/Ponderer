package com.nododiiiii.ponderer.runtime.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.logging.LogUtils;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
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

    private record BindingFilter(ResourceLocation itemId, @Nullable CompoundTag nbt) {
    }

    private static final Set<Registration> REGISTRATIONS = ConcurrentHashMap.newKeySet();

    // Only the transient hovered stack is shared by the native PonderUI bridge.
    static final ThreadLocal<ItemStack> CURRENT_STACK = new ThreadLocal<>();
    private NbtSceneFilter() {}

    /** One runtime owns its bindings; re-registering or closing it never clears another runtime. */
    public static final class Registration implements AutoCloseable {
        private final Map<String, List<BindingFilter>> filters = new ConcurrentHashMap<>();
        private final Map<ResourceLocation, Set<String>> scenes = new ConcurrentHashMap<>();
        public Registration() { REGISTRATIONS.add(this); }
        public void registerScene(ResourceLocation itemId, String sceneId, @Nullable CompoundTag nbtFilter) {
            scenes.computeIfAbsent(itemId, ignored -> ConcurrentHashMap.newKeySet()).add(sceneId);
            filters.compute(sceneId, (key, existing) -> {
                List<BindingFilter> next = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
                BindingFilter binding = new BindingFilter(itemId, nbtFilter);
                if (!next.contains(binding)) next.add(binding);
                return List.copyOf(next);
            });
        }
        public void clear() { filters.clear(); scenes.clear(); }
        @Override public void close() { REGISTRATIONS.remove(this); clear(); }
    }

    private static Set<String> scenesFor(ResourceLocation item) {
        Set<String> result = new java.util.HashSet<>();
        for (Registration owner : REGISTRATIONS) result.addAll(owner.scenes.getOrDefault(item, Set.of()));
        return result;
    }
    @Nullable
    private static List<BindingFilter> bindingsFor(String id) {
        List<BindingFilter> result = null;
        for (Registration owner : REGISTRATIONS) {
            List<BindingFilter> bindings = owner.filters.get(id);
            if (bindings != null) {
                if (result == null) result = new ArrayList<>();
                result.addAll(bindings);
            }
        }
        return result;
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

    /** Builds a preview carrier using either custom NBT, legacy tag data, or 1.21 components. */
    public static ItemStack createStack(ResourceLocation itemId, @Nullable String snbt) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(itemId));
        CompoundTag filter = parseNbt(snbt);
        if (filter == null || filter.isEmpty()) return stack;

        CompoundTag custom = filter.contains("tag", Tag.TAG_COMPOUND) ? filter.getCompound("tag") : filter;
        if (filter.contains("components", Tag.TAG_COMPOUND)) {
            RegistryAccess registryAccess = getRegistryAccess();
            if (registryAccess != null) {
                CompoundTag saved = new CompoundTag();
                saved.putString("id", itemId.toString());
                saved.putInt("count", 1);
                saved.put("components", filter.getCompound("components").copy());
                ItemStack parsed = ItemStack.parseOptional(registryAccess, saved);
                if (!parsed.isEmpty()) return parsed;
            }
            return stack;
        }
        if (!custom.isEmpty()) {
            CompoundTag combined = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            combined.merge(custom);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(combined));
        }
        return stack;
    }

    /**
     * Check whether any registered filters exist for the given item.
     */
    public static boolean hasFilters(ResourceLocation itemId) {
        Set<String> sceneIds = scenesFor(itemId);
        if (sceneIds == null || sceneIds.isEmpty()) return false;
        for (String sid : sceneIds) {
            List<BindingFilter> filters = bindingsFor(sid);
            if (filters != null && filters.stream().anyMatch(filter ->
                filter.itemId().equals(itemId) && filter.nbt() != null)) return true;
        }
        return false;
    }

    /**
     * Check whether at least one scene is visible for the given ItemStack.
     * If no filters are registered for this item, returns true (pass through).
     * If filters exist, returns true if at least one scene has no filter OR matches.
     */
    public static boolean hasVisibleScenes(ItemStack stack, ResourceLocation itemId) {
        Set<String> sceneIds = scenesFor(itemId);
        if (sceneIds == null || sceneIds.isEmpty()) return true;

        for (String sid : sceneIds) {
            if (matchesBindings(stack, bindingsFor(sid))) return true;
        }
        return false;
    }

    /**
     * Filter a compiled scene list based on the current ItemStack.
     * A scene is included when at least one of its bindings for this item matches.
     * If ALL scenes would be filtered out, returns the original list as fallback.
     */
    public static List<PonderScene> filter(ItemStack stack, List<PonderScene> scenes) {
        if (REGISTRATIONS.isEmpty()) return scenes;

        List<PonderScene> filtered = new ArrayList<>();
        boolean anyFiltered = false;

        for (PonderScene scene : scenes) {
            String sceneId = scene.getId().toString();
            List<BindingFilter> filters = bindingsFor(sceneId);
            if (filters == null) {
                filtered.add(scene);
            } else {
                anyFiltered = true;
                if (matchesBindings(stack, filters)) {
                    filtered.add(scene);
                }
            }
        }

        if (!anyFiltered) return scenes;
        return filtered.isEmpty() ? scenes : filtered;
    }

    private static boolean matchesBindings(ItemStack stack, @Nullable List<BindingFilter> filters) {
        if (filters == null) return true;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (BindingFilter filter : filters) {
            if (filter.itemId().equals(itemId) && (filter.nbt() == null || matchesNbt(stack, filter.nbt()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the ItemStack's serialized NBT contains all tags in the filter.
     * Uses subset matching: every key/value in filter must exist in the stack's tag.
     */
    public static boolean matchesNbt(ItemStack stack, CompoundTag filter) {
        if (filter == null || filter.isEmpty()) return true;

        try {
            // Special-case written books: matching by title is more stable than full payload matching.
            if (stack.is(Items.WRITTEN_BOOK)) {
                String requiredTitle = extractWrittenBookTitle(filter);
                if (requiredTitle != null && !requiredTitle.isBlank()) {
                    String actualTitle = extractWrittenBookTitle(stack);
                    return actualTitle != null && requiredTitle.equals(actualTitle);
                }
            }

            CompoundTag customTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

            if (!customTag.isEmpty() && isSubset(filter, customTag)) {
                return true;
            }

            if (filter.contains("tag", Tag.TAG_COMPOUND)) {
                CompoundTag legacyTag = filter.getCompound("tag");
                if (!legacyTag.isEmpty() && isSubset(legacyTag, customTag)) {
                    return true;
                }
            }

            RegistryAccess registryAccess = getRegistryAccess();
            if (registryAccess == null) {
                return false;
            }

            Tag savedTag = stack.saveOptional(registryAccess);
            if (!(savedTag instanceof CompoundTag stackTag)) {
                return false;
            }

            if (isSubsetIgnoringCount(filter, stackTag)) {
                return true;
            }

            if (filter.contains("components", Tag.TAG_COMPOUND)
                    && stackTag.contains("components", Tag.TAG_COMPOUND)
                    && isSubset(filter.getCompound("components"), stackTag.getCompound("components"))) {
                return true;
            }

            return filter.contains("tag", Tag.TAG_COMPOUND)
                    && stackTag.contains("tag", Tag.TAG_COMPOUND)
                    && isSubset(filter.getCompound("tag"), stackTag.getCompound("tag"));
        } catch (Exception e) {
            LOGGER.debug("NBT match check failed", e);
            return false;
        }
    }

    @Nullable
    public static String extractWrittenBookTitleFromFilterSnbt(@Nullable String snbt) {
        CompoundTag parsed = parseNbt(snbt);
        if (parsed == null) return null;
        return extractWrittenBookTitle(parsed);
    }

    @Nullable
    private static String extractWrittenBookTitle(ItemStack stack) {
        CompoundTag customTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String fromCustom = extractWrittenBookTitle(customTag);
        if (fromCustom != null && !fromCustom.isBlank()) {
            return fromCustom;
        }

        RegistryAccess registryAccess = getRegistryAccess();
        if (registryAccess == null) {
            return null;
        }
        Tag savedTag = stack.saveOptional(registryAccess);
        if (!(savedTag instanceof CompoundTag ct)) {
            return null;
        }
        return extractWrittenBookTitle(ct);
    }

    @Nullable
    private static String extractWrittenBookTitle(@Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }

        if (tag.contains("title")) {
            String parsed = parseBookTitleTag(tag.get("title"));
            if (parsed != null && !parsed.isBlank()) return parsed;
        }

        if (tag.contains("tag", Tag.TAG_COMPOUND)) {
            String nested = extractWrittenBookTitle(tag.getCompound("tag"));
            if (nested != null && !nested.isBlank()) return nested;
        }

        // 兼容 1.21+ 组件化写法: components.minecraft:written_book_content.title
        if (tag.contains("components", Tag.TAG_COMPOUND)) {
            CompoundTag components = tag.getCompound("components");
            if (components.contains("minecraft:written_book_content", Tag.TAG_COMPOUND)) {
                CompoundTag content = components.getCompound("minecraft:written_book_content");
                if (content.contains("title")) {
                    String parsed = parseBookTitleTag(content.get("title"));
                    if (parsed != null && !parsed.isBlank()) return parsed;
                }
            }
        }

        return null;
    }

    @Nullable
    private static String parseBookTitleTag(@Nullable Tag titleTag) {
        if (titleTag == null) return null;

        if (titleTag instanceof CompoundTag compound) {
            if (compound.contains("raw", Tag.TAG_STRING)) {
                return normalizeTitleString(compound.getString("raw"));
            }
            if (compound.contains("text", Tag.TAG_STRING)) {
                return normalizeTitleString(compound.getString("text"));
            }
            if (compound.contains("translate", Tag.TAG_STRING)) {
                return normalizeTitleString(compound.getString("translate"));
            }
            return normalizeTitleString(compound.toString());
        }

        return normalizeTitleString(titleTag.getAsString());
    }

    private static String normalizeTitleString(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return s;

        // 兼容 title 为 JSON 文本（如 {"text":"xxx"}）的情况。
        if (s.startsWith("{") || s.startsWith("[")) {
            try {
                JsonElement el = JsonParser.parseString(s);
                String fromJson = extractTextFromJsonComponent(el);
                if (fromJson != null && !fromJson.isBlank()) {
                    return fromJson;
                }
            } catch (JsonSyntaxException ignored) {
            }
        }

        return s;
    }

    @Nullable
    private static String extractTextFromJsonComponent(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) return null;

        if (element.isJsonPrimitive()) {
            try {
                return element.getAsString();
            } catch (Exception ignored) {
                return null;
            }
        }

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("text") && obj.get("text").isJsonPrimitive()) {
                return obj.get("text").getAsString();
            }
            if (obj.has("raw") && obj.get("raw").isJsonPrimitive()) {
                return obj.get("raw").getAsString();
            }
            if (obj.has("translate") && obj.get("translate").isJsonPrimitive()) {
                return obj.get("translate").getAsString();
            }
            if (obj.has("extra") && obj.get("extra").isJsonArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonElement child : obj.getAsJsonArray("extra")) {
                    String part = extractTextFromJsonComponent(child);
                    if (part != null) sb.append(part);
                }
                String joined = sb.toString();
                return joined.isBlank() ? null : joined;
            }
        }

        if (element.isJsonArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonElement child : element.getAsJsonArray()) {
                String part = extractTextFromJsonComponent(child);
                if (part != null) sb.append(part);
            }
            String joined = sb.toString();
            return joined.isBlank() ? null : joined;
        }

        return null;
    }

    @Nullable
    private static RegistryAccess getRegistryAccess() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? null : minecraft.level.registryAccess();
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

    private static boolean isSubsetIgnoringCount(CompoundTag subset, CompoundTag superset) {
        for (String key : subset.getAllKeys()) {
            if ("Count".equals(key) || "count".equals(key)) {
                continue;
            }
            Tag subVal = subset.get(key);
            Tag superVal = superset.get(key);
            if (superVal == null) return false;

            if (subVal instanceof CompoundTag subCompound && superVal instanceof CompoundTag superCompound) {
                if (!isSubsetIgnoringCount(subCompound, superCompound)) return false;
            } else if (!subVal.equals(superVal)) {
                return false;
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
}
