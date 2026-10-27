package com.nododiiiii.ponderer.application.conversion;

import com.nododiiiii.ponderer.exporter.PonderJsCodec;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.feedback.FeedbackLevel;
import com.nododiiiii.ponderer.util.SafePaths;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/** Product use case for importing and exporting bridge files. */
public final class PonderJsConversionService {
    private final java.util.function.BiConsumer<net.minecraft.network.chat.Component, FeedbackLevel> feedback;

    public PonderJsConversionService(java.util.function.BiConsumer<net.minecraft.network.chat.Component, FeedbackLevel> feedback) {
        this.feedback = java.util.Objects.requireNonNull(feedback);
    }

    private static Path getClientScriptsDir() {
        return Minecraft.getInstance().gameDirectory.toPath()
            .resolve("kubejs").resolve("client_scripts").resolve("ponder");
    }

    public int convertToPonderJs(ResourceLocation id) {
        Optional<DslScene> scene = com.nododiiiii.ponderer.application.api.RuntimeAccess.engine().catalog().getScenes().stream()
            .filter(s -> id.toString().equals(s.id))
            .findFirst();
        if (scene.isEmpty()) {
            notifyClient(net.minecraft.network.chat.Component.translatable("ponderer.cmd.scene_not_found", id.toString()), FeedbackLevel.ERROR);
            return 0;
        }

        Path scriptsDir = getClientScriptsDir();
        String flatName = SafePaths.sanitizeWindowsFileName(id.getPath().replace('/', '_'), "scene") + ".ponderer.js";
        Path out = SafePaths.resolveFileName(scriptsDir, flatName);
        if (out == null) {
            notifyClient(net.minecraft.network.chat.Component.translatable("ponderer.cmd.convert.to_failed", id.toString()), FeedbackLevel.ERROR);
            return 0;
        }
        try {
            Files.createDirectories(out.getParent());
            String content = PonderJsCodec.buildPonderJsScript(scene.get(), id.toString());
            Files.writeString(out, content, StandardCharsets.UTF_8);
            notifyClient(net.minecraft.network.chat.Component.translatable("ponderer.cmd.convert.to_done", out.toString()));
            return 1;
        } catch (Exception e) {
            notifyClient(net.minecraft.network.chat.Component.translatable("ponderer.cmd.convert.to_failed", id.toString()), FeedbackLevel.ERROR);
            return 0;
        }
    }

    public int convertAllToPonderJs() {
        int count = 0;
        for (DslScene scene : com.nododiiiii.ponderer.application.api.RuntimeAccess.engine().catalog().getScenes()) {
            if (scene == null || scene.id == null || scene.id.isBlank()) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(scene.id);
            if (id == null) {
                continue;
            }
            count += convertToPonderJs(id);
        }
        notifyClient(net.minecraft.network.chat.Component.translatable("ponderer.cmd.convert.to_all_done", count));
        return count;
    }

    public int convertFromPonderJs(ResourceLocation id) {
        Path scriptsDir = getClientScriptsDir();
        if (!Files.exists(scriptsDir)) {
            notifyClient(net.minecraft.network.chat.Component.translatable("ponderer.cmd.convert.dir_not_found"), FeedbackLevel.ERROR);
            return 0;
        }
        int count = 0;
        try (Stream<Path> paths = Files.walk(scriptsDir)) {
            for (Path p : paths.filter(path -> path.toString().endsWith(".js")).toList()) {
                DslScene scene = PonderJsCodec.parseAnyPonderJsFile(p);
                if (scene == null || !id.toString().equals(scene.id)) {
                    continue;
                }
                if (com.nododiiiii.ponderer.workspace.api.WorkspaceAccess.get().scenes().saveSceneToLocal(scene)) {
                    count++;
                }
            }
        } catch (Exception e) {
            notifyClient(net.minecraft.network.chat.Component.translatable("ponderer.cmd.convert.scan_failed"), FeedbackLevel.ERROR);
            return 0;
        }
        if (com.nododiiiii.ponderer.application.SceneRefreshCoordinator.refreshAndReport() < 0) return 0;
        notifyClient(net.minecraft.network.chat.Component.translatable("ponderer.cmd.convert.from_done", count, id.toString()));
        return count;
    }

    public int convertAllFromPonderJs() {
        Path scriptsDir = getClientScriptsDir();
        if (!Files.exists(scriptsDir)) {
            notifyClient(net.minecraft.network.chat.Component.translatable("ponderer.cmd.convert.dir_not_found"), FeedbackLevel.ERROR);
            return 0;
        }
        int count = 0;
        try (Stream<Path> paths = Files.walk(scriptsDir)) {
            for (Path p : paths.filter(path -> path.toString().endsWith(".js")).toList()) {
                DslScene scene = PonderJsCodec.parseAnyPonderJsFile(p);
                if (scene == null) {
                    continue;
                }
                if (com.nododiiiii.ponderer.workspace.api.WorkspaceAccess.get().scenes().saveSceneToLocal(scene)) {
                    count++;
                }
            }
        } catch (Exception e) {
            notifyClient(net.minecraft.network.chat.Component.translatable("ponderer.cmd.convert.scan_failed"), FeedbackLevel.ERROR);
            return 0;
        }
        if (com.nododiiiii.ponderer.application.SceneRefreshCoordinator.refreshAndReport() < 0) return 0;
        notifyClient(net.minecraft.network.chat.Component.translatable("ponderer.cmd.convert.from_all_done", count));
        return count;
    }

    private void notifyClient(net.minecraft.network.chat.Component message) {
        notifyClient(message, FeedbackLevel.INFO);
    }

    private void notifyClient(net.minecraft.network.chat.Component message, FeedbackLevel level) {
        feedback.accept(message, level);
    }
}
