package com.nododiiiii.ponderer.forge;

import com.google.gson.JsonObject;
import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.FeatureAvailability;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.util.TomlBooleanReader;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class ForgeRecipeConditions {
    private static final String BLUEPRINT_KEY = "enableBlueprintItem";
    private static final String PROJECTOR_KEY = "enableProjector";
    private static final ResourceLocation BLUEPRINT_ENABLED_ID = new ResourceLocation(Ponderer.MODID, "blueprint_enabled");
    private static final ResourceLocation PROJECTOR_ENABLED_ID = new ResourceLocation(Ponderer.MODID, "projector_enabled");

    public static void register() {
        CraftingHelper.register(BlueprintEnabledCondition.Serializer.INSTANCE);
        CraftingHelper.register(ProjectorEnabledCondition.Serializer.INSTANCE);
    }

    private static final class BlueprintEnabledCondition implements ICondition {
        private static final BlueprintEnabledCondition INSTANCE = new BlueprintEnabledCondition();

        private BlueprintEnabledCondition() {
        }

        @Override
        public ResourceLocation getID() {
            return BLUEPRINT_ENABLED_ID;
        }

        @Override
        public boolean test(IContext context) {
            return FeatureAvailability.isBlueprintEnabled(
                () -> readServerConfigBoolean(BLUEPRINT_KEY, ConfigDefaults.BLUEPRINT_ENABLED));
        }

        private static final class Serializer implements IConditionSerializer<BlueprintEnabledCondition> {
            private static final Serializer INSTANCE = new Serializer();

            @Override
            public void write(JsonObject json, BlueprintEnabledCondition value) {
            }

            @Override
            public BlueprintEnabledCondition read(JsonObject json) {
                return BlueprintEnabledCondition.INSTANCE;
            }

            @Override
            public ResourceLocation getID() {
                return BLUEPRINT_ENABLED_ID;
            }
        }
    }

    private static final class ProjectorEnabledCondition implements ICondition {
        private static final ProjectorEnabledCondition INSTANCE = new ProjectorEnabledCondition();

        private ProjectorEnabledCondition() {
        }

        @Override
        public ResourceLocation getID() {
            return PROJECTOR_ENABLED_ID;
        }

        @Override
        public boolean test(IContext context) {
            return FeatureAvailability.isProjectorEnabled(
                () -> readServerConfigBoolean(PROJECTOR_KEY, ConfigDefaults.PROJECTOR_ENABLED));
        }

        private static final class Serializer implements IConditionSerializer<ProjectorEnabledCondition> {
            private static final Serializer INSTANCE = new Serializer();

            @Override
            public void write(JsonObject json, ProjectorEnabledCondition value) {
            }

            @Override
            public ProjectorEnabledCondition read(JsonObject json) {
                return ProjectorEnabledCondition.INSTANCE;
            }

            @Override
            public ResourceLocation getID() {
                return PROJECTOR_ENABLED_ID;
            }
        }
    }

    private static boolean readServerConfigBoolean(String key, boolean defaultValue) {
        Boolean loadedValue = readLoadedServerConfigBoolean(key);
        if (loadedValue != null) {
            return loadedValue;
        }

        Path configPath = FMLPaths.CONFIGDIR.get().resolve(Ponderer.MODID + "-server.toml");
        return TomlBooleanReader.readBoolean(configPath, key).orElse(defaultValue);
    }

    private static Boolean readLoadedServerConfigBoolean(String key) {
        try {
            if (BLUEPRINT_KEY.equals(key)) {
                return Config.ENABLE_BLUEPRINT_ITEM.get();
            }
            if (PROJECTOR_KEY.equals(key)) {
                return Config.ENABLE_PROJECTOR.get();
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static final class ConfigDefaults {
        private static final boolean BLUEPRINT_ENABLED = true;
        private static final boolean PROJECTOR_ENABLED = true;

        private ConfigDefaults() {
        }
    }

    private ForgeRecipeConditions() {
    }
}
