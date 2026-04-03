package com.nododiiiii.ponderer.mixin;

import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.util.SafePaths;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Mixin(PonderSceneRegistry.class)
public class PonderSceneRegistryMixin {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Inject(
        method = "loadSchematic(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void ponderer$loadLocalSchematicMoj(ResourceLocation location,
                                                       CallbackInfoReturnable<StructureTemplate> cir) {
        ponderer$loadLocalSchematicImpl(location, cir);
    }

    @Inject(
        method = "loadSchematic(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void ponderer$loadLocalSchematicManager(ResourceManager resourceManager,
                                                            ResourceLocation location,
                                                            CallbackInfoReturnable<StructureTemplate> cir) {
        ponderer$loadLocalSchematicImpl(location, cir);
    }

    private static void ponderer$loadLocalSchematicImpl(ResourceLocation location,
                                                        CallbackInfoReturnable<StructureTemplate> cir) {
        if (Ponderer.MODID.equals(location.getNamespace())) {
            // Priority 1: user's config/ponderer/structures/ folder (flat + pack subdirectories)
            Path path = SceneStore.resolveStructurePath(location.getPath(), null);
            if (path != null && Files.exists(path)) {
                try (InputStream stream = Files.newInputStream(path)) {
                    cir.setReturnValue(PonderSceneRegistry.loadSchematic(stream));
                } catch (Exception e) {
                    LOGGER.error("Failed to read ponderer schematic: {}", path, e);
                }
                return;
            }

            // Priority 2: auto-copy built-in structure to local folder, then load from local
            if (SceneStore.ensureBuiltinStructure(location.getPath())) {
                Path localPath = SceneStore.getStructurePath(location);
                if (localPath != null && Files.exists(localPath)) {
                    try (InputStream stream = Files.newInputStream(localPath)) {
                        cir.setReturnValue(PonderSceneRegistry.loadSchematic(stream));
                    } catch (Exception e) {
                        LOGGER.error("Failed to read ponderer schematic after copy: {}", localPath, e);
                    }
                    return;
                }
            }

            // Priority 3: load directly from jar without copying
            InputStream builtinStream = SceneStore.openBuiltinStructure(location.getPath());
            if (builtinStream != null) {
                try (builtinStream) {
                    cir.setReturnValue(PonderSceneRegistry.loadSchematic(builtinStream));
                } catch (Exception e) {
                    LOGGER.error("Failed to read built-in ponderer schematic: {}", location, e);
                }
                return;
            }

            LOGGER.warn("Ponderer schematic missing: {}", location);
            return;
        }

        // Native singleplayer generated structures fallback:
        // saves/<world>/generated/<namespace>/structures/<path>.nbt
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return;
        }
        Path root = server.getWorldPath(LevelResource.ROOT);
        Path generatedPath = SafePaths.resolveRelativePath(
            root.resolve("generated"),
            location.getNamespace() + "/structures/" + location.getPath() + ".nbt");

        if (generatedPath == null || !Files.exists(generatedPath)) {
            return;
        }

        try (InputStream stream = Files.newInputStream(generatedPath)) {
            cir.setReturnValue(PonderSceneRegistry.loadSchematic(stream));
        } catch (Exception e) {
            LOGGER.error("Failed to read generated schematic: {}", generatedPath, e);
        }
    }
}
