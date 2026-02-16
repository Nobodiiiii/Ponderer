package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.ponder.SceneStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

        ResourceLocation source = ResourceLocation.tryParse(payload.sourceId());
        if (source == null) {
            player.sendSystemMessage(Component.translatable("ponderer.cmd.download.invalid_id", payload.sourceId()));
            PondererNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new DownloadStructureResultPayload(payload.sourceId(), "", false,
                    "Invalid structure id"));
            return;
        }

        Path sourcePath = resolveSourcePath(player, source);

        if (sourcePath == null || !Files.exists(sourcePath)) {
            player.sendSystemMessage(Component.translatable("ponderer.cmd.download.not_found", source.toString()));
            PondererNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new DownloadStructureResultPayload(source.toString(), "", false,
                    "Structure not found"));
            return;
        }

        ResourceLocation target = source.getNamespace().equals(Ponderer.MODID)
            ? source
            : new ResourceLocation(Ponderer.MODID, source.getPath());
        try {
            byte[] bytes = Files.readAllBytes(sourcePath);
            boolean ok = SceneStore.saveStructureToServer(player.server, target.toString(), bytes);
            if (!ok) {
                player.sendSystemMessage(Component.translatable("ponderer.cmd.download.import_failed", source.toString()));
                PondererNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new DownloadStructureResultPayload(source.toString(), target.toString(), false,
                        "Import failed"));
                return;
            }

            List<SyncResponsePayload.FileEntry> scripts = SceneStore.collectServerScripts(player.server);
            List<SyncResponsePayload.FileEntry> structures = SceneStore.collectServerStructures(player.server);
            PondererNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncResponsePayload(scripts, structures));

            PondererNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new DownloadStructureResultPayload(source.toString(), target.toString(), true,
                    "OK"));

            player.sendSystemMessage(Component.translatable("ponderer.cmd.download.done", source.toString(), target.toString()));
        } catch (Exception e) {
            player.sendSystemMessage(Component.translatable("ponderer.cmd.download.read_failed", source.toString()));
            PondererNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new DownloadStructureResultPayload(source.toString(), target.toString(), false,
                    "Read failed"));
        }
    }

    private static Path resolveSourcePath(ServerPlayer player, ResourceLocation source) {
        if (Ponderer.MODID.equals(source.getNamespace())) {
            Path serverRoot = SceneStore.getServerStructureDir(player.server);
            Path direct = serverRoot.resolve(source.getPath() + ".nbt");
            if (Files.exists(direct)) {
                return direct;
            }
            return null;
        }

        Path generatedPath = player.server.getWorldPath(LevelResource.ROOT)
            .resolve("generated")
            .resolve(source.getNamespace())
            .resolve("structures")
            .resolve(source.getPath() + ".nbt");
        if (Files.exists(generatedPath)) {
            return generatedPath;
        }

        Path serverRoot = SceneStore.getServerStructureDir(player.server);
        Path fallback = serverRoot.resolve(source.getNamespace()).resolve(source.getPath() + ".nbt");
        if (Files.exists(fallback)) {
            return fallback;
        }
        return null;
    }
}
