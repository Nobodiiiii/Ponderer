package com.nododiiiii.ponderer.mixin;

import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.ponder.SceneStore;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.server.packs.resources.ResourceManager;
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
        method = "loadSchematic(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void ponderer$loadLocalSchematic(ResourceManager resourceManager, ResourceLocation location,
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

            // Priority 2: built-in resources bundled in the jar
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
        Path generatedPath = root.resolve("generated")
            .resolve(location.getNamespace())
            .resolve("structures")
            .resolve(location.getPath() + ".nbt");

        if (!Files.exists(generatedPath)) {
            return;
        }

        try (InputStream stream = Files.newInputStream(generatedPath)) {
            cir.setReturnValue(PonderSceneRegistry.loadSchematic(stream));
        } catch (Exception e) {
            LOGGER.error("Failed to read generated schematic: {}", generatedPath, e);
        }
    }
}
