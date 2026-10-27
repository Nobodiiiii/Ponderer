package com.nododiiiii.ponderer.sync.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.sync.server.AutoRemoteSyncService;
import com.nododiiiii.ponderer.application.security.UploadPermissions;
import com.nododiiiii.ponderer.util.SafePaths;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import com.nododiiiii.ponderer.sync.protocol.DownloadStructureResultPayload;
import com.nododiiiii.ponderer.sync.protocol.DownloadStructurePayload;
import static com.nododiiiii.ponderer.sync.protocol.DownloadStructurePayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class DownloadStructureHandler {
    private DownloadStructureHandler() {}

    public static void handle(DownloadStructurePayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!UploadPermissions.canUpload(player)) {

            PondererServices.NETWORK.sendToPlayer(player,
                    new DownloadStructureResultPayload(payload.sourceId(), "", false, "ponderer.cmd.download.no_permission"));
            return;
        }

        ResourceLocation source = ResourceLocation.tryParse(payload.sourceId());
        if (source == null) {

            PondererServices.NETWORK.sendToPlayer(player, new DownloadStructureResultPayload(payload.sourceId(), "", false,
                    "ponderer.cmd.download.invalid_id"));
            return;
        }

        Path sourcePath = resolveSourcePath(player, source);

        if (sourcePath == null || !Files.exists(sourcePath)) {

            PondererServices.NETWORK.sendToPlayer(player, new DownloadStructureResultPayload(source.toString(), "", false,
                    "ponderer.cmd.download.not_found"));
            return;
        }

        ResourceLocation target = source.getNamespace().equals(Ponderer.MODID)
            ? source
            : ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, source.getPath());
        try {
            byte[] bytes = Files.readAllBytes(sourcePath);
            boolean ok = com.nododiiiii.ponderer.workspace.api.WorkspaceAccess.get().structures().saveStructureToServer(player.server, target.toString(), null, bytes);
            if (!ok) {

                PondererServices.NETWORK.sendToPlayer(player, new DownloadStructureResultPayload(source.toString(), target.toString(), false,
                        "ponderer.cmd.download.import_failed"));
                return;
            }

            com.nododiiiii.ponderer.sync.server.SyncSender.sendBatched(player);
            AutoRemoteSyncService.onRemoteWorkspaceChanged(player.server);

            PondererServices.NETWORK.sendToPlayer(player, new DownloadStructureResultPayload(source.toString(), target.toString(), true,
                    "ponderer.cmd.download.done"));

        } catch (Exception e) {

            PondererServices.NETWORK.sendToPlayer(player, new DownloadStructureResultPayload(source.toString(), target.toString(), false,
                    "ponderer.cmd.download.read_failed"));
        }
    }

    private static Path resolveSourcePath(ServerPlayer player, ResourceLocation source) {
        if (Ponderer.MODID.equals(source.getNamespace())) {
            Path direct = com.nododiiiii.ponderer.workspace.api.WorkspaceAccess.get().layout().resolveServerStructurePath(player.server, source, null);
            if (direct != null && Files.exists(direct)) {
                return direct;
            }
            Path serverRoot = com.nododiiiii.ponderer.workspace.api.WorkspaceAccess.get().layout().getServerStructureDir(player.server);
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

        Path serverRoot = com.nododiiiii.ponderer.workspace.api.WorkspaceAccess.get().layout().getServerStructureDir(player.server);
        Path fallback = SafePaths.resolveRelativePath(serverRoot, source.getNamespace() + "/" + source.getPath() + ".nbt");
        if (fallback != null && Files.exists(fallback)) {
            return fallback;
        }
        return null;
    }
}
