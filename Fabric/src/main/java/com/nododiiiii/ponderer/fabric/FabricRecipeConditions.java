package com.nododiiiii.ponderer.fabric;

import com.nododiiiii.ponderer.FeatureAvailability;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.util.TomlBooleanReader;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;

public final class FabricRecipeConditions {
    private static final String BLUEPRINT_KEY = "enableBlueprintItem";
    private static final String PROJECTOR_KEY = "enableProjector";

    public static void register() {
        ResourceConditions.register(id("blueprint_enabled"),
            json -> FeatureAvailability.isBlueprintEnabled(
                () -> readServerConfigBoolean(BLUEPRINT_KEY, ConfigDefaults.BLUEPRINT_ENABLED)));
        ResourceConditions.register(id("projector_enabled"),
            json -> FeatureAvailability.isProjectorEnabled(
                () -> readServerConfigBoolean(PROJECTOR_KEY, ConfigDefaults.PROJECTOR_ENABLED)));
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(Ponderer.MODID, path);
    }

    private static boolean readServerConfigBoolean(String key, boolean defaultValue) {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(Ponderer.MODID + "-server.toml");
        return TomlBooleanReader.readBoolean(configPath, key).orElse(defaultValue);
    }

    private static final class ConfigDefaults {
        private static final boolean BLUEPRINT_ENABLED = true;
        private static final boolean PROJECTOR_ENABLED = true;

        private ConfigDefaults() {
        }
    }

    private FabricRecipeConditions() {
    }
}
