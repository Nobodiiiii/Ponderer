package com.nododiiiii.ponderer.neoforge;

import com.mojang.serialization.MapCodec;
import com.nododiiiii.ponderer.FeatureAvailability;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.util.TomlBooleanReader;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.nio.file.Path;

public final class NeoForgeRecipeConditions {
    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS =
        DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, Ponderer.MODID);
    private static final String BLUEPRINT_KEY = "enableBlueprintItem";
    private static final String PROJECTOR_KEY = "enableProjector";

    public static void register(IEventBus modEventBus) {
        CONDITION_CODECS.register("blueprint_enabled", () -> BlueprintEnabledCondition.CODEC);
        CONDITION_CODECS.register("projector_enabled", () -> ProjectorEnabledCondition.CODEC);
        CONDITION_CODECS.register(modEventBus);
    }

    private static final class BlueprintEnabledCondition implements ICondition {
        private static final BlueprintEnabledCondition INSTANCE = new BlueprintEnabledCondition();
        private static final MapCodec<BlueprintEnabledCondition> CODEC = MapCodec.unit(INSTANCE).stable();

        private BlueprintEnabledCondition() {
        }

        @Override
        public boolean test(IContext context) {
            return FeatureAvailability.isBlueprintEnabled(
                () -> readServerConfigBoolean(BLUEPRINT_KEY, ConfigDefaults.BLUEPRINT_ENABLED));
        }

        @Override
        public MapCodec<? extends ICondition> codec() {
            return CODEC;
        }
    }

    private static final class ProjectorEnabledCondition implements ICondition {
        private static final ProjectorEnabledCondition INSTANCE = new ProjectorEnabledCondition();
        private static final MapCodec<ProjectorEnabledCondition> CODEC = MapCodec.unit(INSTANCE).stable();

        private ProjectorEnabledCondition() {
        }

        @Override
        public boolean test(IContext context) {
            return FeatureAvailability.isProjectorEnabled(
                () -> readServerConfigBoolean(PROJECTOR_KEY, ConfigDefaults.PROJECTOR_ENABLED));
        }

        @Override
        public MapCodec<? extends ICondition> codec() {
            return CODEC;
        }
    }

    private NeoForgeRecipeConditions() {
    }

    private static boolean readServerConfigBoolean(String key, boolean defaultValue) {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(Ponderer.MODID + "-server.toml");
        return TomlBooleanReader.readBoolean(configPath, key).orElse(defaultValue);
    }

    private static final class ConfigDefaults {
        private static final boolean BLUEPRINT_ENABLED = true;
        private static final boolean PROJECTOR_ENABLED = true;

        private ConfigDefaults() {
        }
    }
}
