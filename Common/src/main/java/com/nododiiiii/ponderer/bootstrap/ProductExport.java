package com.nododiiiii.ponderer.bootstrap;

import com.nododiiiii.ponderer.exporter.*;
import com.nododiiiii.ponderer.scene.SceneSnapshot;
import java.nio.file.*;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;

/** Product-only resource/catalog adapter for the standalone source exporter. */
public final class ProductExport {
    private ProductExport() {}
    public static JavaModuleExportRequest request(Path root, Set<String> keys) {
        return new JavaModuleExportRequest(root, keys, com.nododiiiii.ponderer.application.api.RuntimeAccess.scenes().stream()
            .map(SceneSnapshot::of).toList());
    }
    public static JavaModuleExportService service() {
        return new JavaModuleExportService((id, pack) -> {
            if ("ponderer".equals(id.getNamespace())) {
                if (pack != null && !pack.isBlank()) {
                    Path packed = com.nododiiiii.ponderer.workspace.api.WorkspaceAccess.get().layout().resolveLocalSyncStructurePath(id, pack);
                    if (packed != null && Files.exists(packed)) return packed;
                }
                Path local = com.nododiiiii.ponderer.workspace.api.WorkspaceAccess.get().layout().getStructurePath(id);
                if (local != null && Files.exists(local)) return local;
            }
            var server = Minecraft.getInstance().getSingleplayerServer();
            if (server != null) {
                Path generated = server.getWorldPath(LevelResource.ROOT).resolve("generated").resolve(id.getNamespace())
                    .resolve("structures").resolve(id.getPath() + ".nbt").normalize();
                if (Files.exists(generated)) return generated;
            }
            var asset = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "ponder/" + id.getPath() + ".nbt");
            var resource = Minecraft.getInstance().getResourceManager().getResource(asset);
            if (resource.isEmpty()) return null;
            Path cache = com.nododiiiii.ponderer.workspace.api.WorkspaceAccess.get().layout().getReadonlyCacheRoot()
                .getParent().resolve("java-export").toAbsolutePath().normalize();
            Path destination = cache.resolve(id.getNamespace()).resolve(id.getPath() + ".nbt").normalize();
            if (!destination.startsWith(cache)) return null;
            try (var input = resource.get().open()) {
                Files.createDirectories(destination.getParent());
                Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
                return destination;
            } catch (java.io.IOException error) {
                com.mojang.logging.LogUtils.getLogger().warn("Cannot prepare exported structure {}", asset, error);
                return null;
            }
        });
    }
}
