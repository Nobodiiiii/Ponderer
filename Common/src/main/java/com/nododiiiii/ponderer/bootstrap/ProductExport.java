package com.nododiiiii.ponderer.bootstrap;

import com.nododiiiii.ponderer.exporter.*;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.scene.SceneSnapshot;
import java.nio.file.*;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;

/** Product-only resource/catalog adapter for the standalone source exporter. */
public final class ProductExport {
    private ProductExport() {}
    public static JavaModuleExportRequest request(Path root, Set<String> keys) {
        return new JavaModuleExportRequest(root, keys, ProductRuntime.engine().catalog().getScenes().stream()
            .map(SceneSnapshot::of).toList());
    }
    public static JavaModuleExportService service() {
        return new JavaModuleExportService((id, pack) -> {
            if ("ponderer".equals(id.getNamespace())) {
                if (pack != null && !pack.isBlank()) {
                    Path packed = SceneStore.resolveLocalSyncStructurePath(id, pack);
                    if (packed != null && Files.exists(packed)) return packed;
                }
                Path local = SceneStore.getStructurePath(id);
                return local != null && Files.exists(local) ? local : null;
            }
            var server = Minecraft.getInstance().getSingleplayerServer();
            if (server == null) return null;
            Path generated = server.getWorldPath(LevelResource.ROOT).resolve("generated").resolve(id.getNamespace())
                .resolve("structures").resolve(id.getPath() + ".nbt").normalize();
            return Files.exists(generated) ? generated : null;
        });
    }
}
