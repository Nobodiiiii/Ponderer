package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.ponder.SceneStore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record DownloadStructurePayload(String sourceId) implements CustomPacketPayload {
    public static final Type<DownloadStructurePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "download_structure"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DownloadStructurePayload> CODEC =
        StreamCodec.of(DownloadStructurePayload::encode, DownloadStructurePayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, DownloadStructurePayload payload) {
        buf.writeUtf(payload.sourceId());
    }

    private static DownloadStructurePayload decode(RegistryFriendlyByteBuf buf) {
        return new DownloadStructurePayload(buf.readUtf());
    }

    public static void handle(DownloadStructurePayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }

        ResourceLocation source = ResourceLocation.tryParse(payload.sourceId());
        if (source == null) {
            player.sendSystemMessage(Component.translatable("ponderer.cmd.download.invalid_id", payload.sourceId()));
            PacketDistributor.sendToPlayer(player,
                new DownloadStructureResultPayload(payload.sourceId(), "", false,
                    "Invalid structure id"));
            return;
        }

        Path sourcePath = resolveSourcePath(player, source);

        if (sourcePath == null || !Files.exists(sourcePath)) {
            player.sendSystemMessage(Component.translatable("ponderer.cmd.download.not_found", source.toString()));
            PacketDistributor.sendToPlayer(player,
                new DownloadStructureResultPayload(source.toString(), "", false,
                    "Structure not found"));
            return;
        }

        ResourceLocation target = source.getNamespace().equals(Ponderer.MODID)
            ? source
            : ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, source.getPath());
        try {
            byte[] bytes = Files.readAllBytes(sourcePath);
            boolean ok = SceneStore.saveStructureToServer(player.server, target.toString(), bytes);
            if (!ok) {
                player.sendSystemMessage(Component.translatable("ponderer.cmd.download.import_failed", source.toString()));
                PacketDistributor.sendToPlayer(player,
                    new DownloadStructureResultPayload(source.toString(), target.toString(), false,
                        "Import failed"));
                return;
            }

            List<SyncResponsePayload.FileEntry> scripts = SceneStore.collectServerScripts(player.server);
            List<SyncResponsePayload.FileEntry> structures = SceneStore.collectServerStructures(player.server);
            PacketDistributor.sendToPlayer(player, new SyncResponsePayload(scripts, structures));

            PacketDistributor.sendToPlayer(player,
                new DownloadStructureResultPayload(source.toString(), target.toString(), true,
                    "OK"));

            player.sendSystemMessage(Component.translatable("ponderer.cmd.download.done", source.toString(), target.toString()));
        } catch (Exception e) {
            player.sendSystemMessage(Component.translatable("ponderer.cmd.download.read_failed", source.toString()));
            PacketDistributor.sendToPlayer(player,
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
