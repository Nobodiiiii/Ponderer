package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.platform.PondererServices;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ponder.UploadPermissions;
import com.nododiiiii.ponderer.util.SafePaths;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
public record DownloadStructurePayload(String sourceId) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(sourceId());
    }

    public static DownloadStructurePayload decode(FriendlyByteBuf buf) {
        return new DownloadStructurePayload(buf.readUtf());
    }

    public static void handle(DownloadStructurePayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!UploadPermissions.canUpload(player)) {
            player.sendSystemMessage(Component.translatable("ponderer.cmd.download.no_permission"));
            PondererServices.NETWORK.sendToPlayer(player,
                    new DownloadStructureResultPayload(payload.sourceId(), "", false, "No permission"));
            return;
        }

        ResourceLocation source = ResourceLocation.tryParse(payload.sourceId());
        if (source == null) {
            player.sendSystemMessage(Component.translatable("ponderer.cmd.download.invalid_id", payload.sourceId()));
            PondererServices.NETWORK.sendToPlayer(player, new DownloadStructureResultPayload(payload.sourceId(), "", false,
                    "Invalid structure id"));
            return;
        }

        Path sourcePath = resolveSourcePath(player, source);

        if (sourcePath == null || !Files.exists(sourcePath)) {
            player.sendSystemMessage(Component.translatable("ponderer.cmd.download.not_found", source.toString()));
            PondererServices.NETWORK.sendToPlayer(player, new DownloadStructureResultPayload(source.toString(), "", false,
                    "Structure not found"));
            return;
        }

        ResourceLocation target = source.getNamespace().equals(Ponderer.MODID)
            ? source
            : new ResourceLocation(Ponderer.MODID, source.getPath());
        try {
            byte[] bytes = Files.readAllBytes(sourcePath);
            boolean ok = SceneStore.saveStructureToServer(player.server, target.toString(), null, bytes);
            if (!ok) {
                player.sendSystemMessage(Component.translatable("ponderer.cmd.download.import_failed", source.toString()));
                PondererServices.NETWORK.sendToPlayer(player, new DownloadStructureResultPayload(source.toString(), target.toString(), false,
                        "Import failed"));
                return;
            }

            SyncResponsePayload.sendBatched(player);

            PondererServices.NETWORK.sendToPlayer(player, new DownloadStructureResultPayload(source.toString(), target.toString(), true,
                    "OK"));

            player.sendSystemMessage(Component.translatable("ponderer.cmd.download.done", source.toString(), target.toString()));
        } catch (Exception e) {
            player.sendSystemMessage(Component.translatable("ponderer.cmd.download.read_failed", source.toString()));
            PondererServices.NETWORK.sendToPlayer(player, new DownloadStructureResultPayload(source.toString(), target.toString(), false,
                    "Read failed"));
        }
    }

    private static Path resolveSourcePath(ServerPlayer player, ResourceLocation source) {
        if (Ponderer.MODID.equals(source.getNamespace())) {
            Path direct = SceneStore.resolveServerStructurePath(player.server, source, null);
            if (direct != null && Files.exists(direct)) {
                return direct;
            }
            Path serverRoot = SceneStore.getServerStructureDir(player.server);
            Path legacy = SafePaths.resolveRelativePath(serverRoot, source.getPath() + ".nbt");
            if (legacy != null && Files.exists(legacy)) {
                return legacy;
            }
            return null;
        }

        Path generatedRoot = player.server.getWorldPath(LevelResource.ROOT).resolve("generated");
        Path generatedPath = SafePaths.resolveRelativePath(
            generatedRoot,
            source.getNamespace() + "/structures/" + source.getPath() + ".nbt"
        );
        if (generatedPath != null && Files.exists(generatedPath)) {
            return generatedPath;
        }

        Path serverRoot = SceneStore.getServerStructureDir(player.server);
        Path fallback = SafePaths.resolveRelativePath(serverRoot, source.getNamespace() + "/" + source.getPath() + ".nbt");
        if (fallback != null && Files.exists(fallback)) {
            return fallback;
        }
        return null;
    }
}
