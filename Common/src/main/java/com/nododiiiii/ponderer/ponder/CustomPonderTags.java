package com.nododiiiii.ponderer.ponder;

import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.scene.LocalizedText;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderTag;
import net.createmod.ponder.foundation.registration.PonderTagRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Scene-owned category definitions. References in other scenes use the same global Ponder ID. */
public final class CustomPonderTags {
    public static final String PREFIX = "ponderer:";
    private static final String LEGACY_PATH_PREFIX = "custom/";
    private static final Pattern NAME = Pattern.compile("[a-z0-9_.-]+");
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter()).create();
    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, DslScene.CustomTag>>() {}.getType();

    private CustomPonderTags() {
    }

    public static boolean isName(@Nullable String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /** The editor shows a single name; keep legacy stored IDs readable without changing references. */
    public static String editorName(String id) {
        String path = ResourceLocation.parse(id).getPath();
        if (path.startsWith(LEGACY_PATH_PREFIX)) {
            path = path.substring(LEGACY_PATH_PREFIX.length());
        }
        return path.replace('/', '_');
    }

    public static boolean isCustomId(@Nullable String id) {
        if (PondererAttribution.isTag(id)) {
            return false;
        }
        ResourceLocation location = id == null ? null : ResourceLocation.tryParse(id);
        return location != null && "ponderer".equals(location.getNamespace())
            && (isName(location.getPath()) || (location.getPath().startsWith(LEGACY_PATH_PREFIX)
                && location.getPath().length() > LEGACY_PATH_PREFIX.length()));
    }

    public static Map<String, DslScene.CustomTag> ownedBy(@Nullable DslScene scene) {
        return scene == null || scene.customTags == null ? Map.of() : scene.customTags;
    }

    public static Map<String, DslScene.CustomTag> collect(Collection<DslScene> scenes) {
        Map<String, DslScene.CustomTag> result = new LinkedHashMap<>();
        for (DslScene scene : scenes) {
            ownedBy(scene).forEach((id, definition) -> {
                if (isCustomId(id) && definition != null) {
                    result.putIfAbsent(id, definition);
                }
            });
        }
        return result;
    }

    /** Include referenced definitions in standalone Java exports, even if another scene owns them. */
    public static Map<String, DslScene.CustomTag> forScene(DslScene scene) {
        Map<String, DslScene.CustomTag> result = new LinkedHashMap<>(collect(java.util.List.of(scene)));
        if (scene.tags != null) {
            Map<String, DslScene.CustomTag> all = collect(com.nododiiiii.ponderer.bootstrap.ProductRuntime.engine().catalog().getScenes());
            for (String id : scene.tags) {
                if (all.containsKey(id)) {
                    result.putIfAbsent(id, all.get(id));
                }
            }
        }
        return result;
    }

    public static String encode(@Nullable Map<String, DslScene.CustomTag> definitions) {
        return GSON.toJson(definitions == null ? Map.of() : definitions, MAP_TYPE);
    }

    public static Map<String, DslScene.CustomTag> decode(String json) {
        Map<String, DslScene.CustomTag> result = GSON.fromJson(json, MAP_TYPE);
        return result == null ? new LinkedHashMap<>() : result;
    }

    public static Map<String, DslScene.CustomTag> copy(@Nullable Map<String, DslScene.CustomTag> definitions) {
        return decode(encode(definitions));
    }

    public static Item iconItem(DslScene.CustomTag definition) {
        ResourceLocation icon = definition.icon == null ? null : ResourceLocation.tryParse(definition.icon);
        return icon == null ? Items.BOOK : BuiltInRegistries.ITEM.getOptional(icon)
            .filter(item -> item != Items.AIR).orElse(Items.BOOK);
    }

    public static PonderTag preview(String id, DslScene.CustomTag definition) {
        Item icon = iconItem(definition);
        return new PonderTag(ResourceLocation.parse(id), null, icon.getDefaultInstance(), icon.getDefaultInstance()) {
            @Override
            public String getTitle() {
                String title = definition.title == null ? "" : definition.title.resolve();
                return title.isBlank() ? id : title;
            }

            @Override
            public String getDescription() {
                return definition.description == null ? "" : definition.description.resolve();
            }
        };
    }

    public static void register() {
        PonderTagRegistry registry = (PonderTagRegistry) PonderIndex.getTagAccess();
        collect(com.nododiiiii.ponderer.bootstrap.ProductRuntime.engine().catalog().getScenes()).forEach((id, definition) -> {
            // PonderTag's default getters read static lang files outside editing mode.
            // Register an instance with dynamic getters so scene text also follows language changes.
            PonderTag tag = preview(id, definition);
            registry.registerTag(tag);
            registry.listTag(tag);
        });
    }
}
