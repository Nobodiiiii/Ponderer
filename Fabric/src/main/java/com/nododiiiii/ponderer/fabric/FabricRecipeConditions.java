package com.nododiiiii.ponderer.fabric;

import com.mojang.serialization.MapCodec;
import com.nododiiiii.ponderer.FeatureAvailability;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.util.TomlBooleanReader;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public final class FabricRecipeConditions {
    private static final ResourceConditionType<BlueprintEnabledCondition> BLUEPRINT_ENABLED =
        ResourceConditionType.create(id("blueprint_enabled"), MapCodec.unit(BlueprintEnabledCondition.INSTANCE).stable());
    private static final ResourceConditionType<ProjectorEnabledCondition> PROJECTOR_ENABLED =
        ResourceConditionType.create(id("projector_enabled"), MapCodec.unit(ProjectorEnabledCondition.INSTANCE).stable());
    private static final String BLUEPRINT_KEY = "enableBlueprintItem";
    private static final String PROJECTOR_KEY = "enableProjector";

    public static void register() {
        ResourceConditions.register(BLUEPRINT_ENABLED);
        ResourceConditions.register(PROJECTOR_ENABLED);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, path);
    }

    private static final class BlueprintEnabledCondition implements ResourceCondition {
        private static final BlueprintEnabledCondition INSTANCE = new BlueprintEnabledCondition();

        private BlueprintEnabledCondition() {
        }

        @Override
        public ResourceConditionType<?> getType() {
            return BLUEPRINT_ENABLED;
        }

        @Override
        public boolean test(@Nullable HolderLookup.Provider registryLookup) {
            return FeatureAvailability.isBlueprintEnabled(
                () -> readServerConfigBoolean(BLUEPRINT_KEY, ConfigDefaults.BLUEPRINT_ENABLED));
        }
    }

    private static final class ProjectorEnabledCondition implements ResourceCondition {
        private static final ProjectorEnabledCondition INSTANCE = new ProjectorEnabledCondition();

        private ProjectorEnabledCondition() {
        }

        @Override
        public ResourceConditionType<?> getType() {
            return PROJECTOR_ENABLED;
        }

        @Override
        public boolean test(@Nullable HolderLookup.Provider registryLookup) {
            return FeatureAvailability.isProjectorEnabled(
                () -> readServerConfigBoolean(PROJECTOR_KEY, ConfigDefaults.PROJECTOR_ENABLED));
        }
    }

    private FabricRecipeConditions() {
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
}
