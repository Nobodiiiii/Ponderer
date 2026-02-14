package com.nododiiiii.ponderer.network;

import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ponder.UploadPermissions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public record UploadScenePayload(String sceneId, String json,
                                 List<StructureEntry> structures,
                                 String mode, String lastSyncHash) implements CustomPacketPayload {
    public static final Type<UploadScenePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "upload_scene"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UploadScenePayload> CODEC =
        StreamCodec.of(UploadScenePayload::encode, UploadScenePayload::decode);

    private static final Logger LOGGER = LogUtils.getLogger();

    public record StructureEntry(String id, byte[] bytes) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, UploadScenePayload payload) {
        buf.writeUtf(payload.sceneId());
        buf.writeUtf(payload.json());
        buf.writeVarInt(payload.structures().size());
        for (StructureEntry entry : payload.structures()) {
            buf.writeUtf(entry.id());
            buf.writeByteArray(entry.bytes());
        }
        buf.writeUtf(payload.mode() == null ? "check" : payload.mode());
        buf.writeUtf(payload.lastSyncHash() == null ? "" : payload.lastSyncHash());
    }

    private static UploadScenePayload decode(RegistryFriendlyByteBuf buf) {
        String sceneId = buf.readUtf();
        String json = buf.readUtf();
        int size = buf.readVarInt();
        List<StructureEntry> structures = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            structures.add(new StructureEntry(buf.readUtf(), buf.readByteArray()));
        }
        String mode = buf.readUtf();
        String lastSyncHash = buf.readUtf();
        return new UploadScenePayload(sceneId, json, structures, mode, lastSyncHash);
    }

    public static void handle(UploadScenePayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!UploadPermissions.canUpload(player)) {
            player.sendSystemMessage(Component.translatable("ponderer.cmd.push.no_permission"));
            return;
        }

        String pushMode = payload.mode() == null ? "check" : payload.mode();

        // Conflict detection for non-force push
        if (!"force".equals(pushMode)) {
            String lastSyncHash = payload.lastSyncHash() == null ? "" : payload.lastSyncHash();
            String serverHash = computeServerSceneHash(player.server, payload.sceneId());

            if (!serverHash.isEmpty() && !lastSyncHash.isEmpty() && !serverHash.equals(lastSyncHash)) {
                // Server file was modified since last sync - conflict
                player.sendSystemMessage(Component.translatable("ponderer.cmd.push.server_conflict", payload.sceneId()));
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new UploadResponsePayload(payload.sceneId(), "conflict"));
                return;
            }
        }

        boolean ok = SceneStore.saveToServer(player.server, payload.sceneId(), payload.json());
        if (ok && payload.structures() != null) {
            for (StructureEntry entry : payload.structures()) {
                if (entry == null || entry.id() == null || entry.id().isBlank() || entry.bytes() == null) {
                    continue;
                }
                ok = SceneStore.saveStructureToServer(player.server, entry.id(), entry.bytes()) && ok;
            }
        }

        if (ok) {
            player.sendSystemMessage(Component.translatable("ponderer.cmd.push.upload_ok", payload.sceneId()));
            // Compute new server hash after write and send it back to client for SyncMeta update
            String newHash = computeServerSceneHash(player.server, payload.sceneId());
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new UploadResponsePayload(payload.sceneId(), "ok:" + newHash));
        } else {
            player.sendSystemMessage(Component.translatable("ponderer.cmd.push.upload_failed", payload.sceneId()));
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new UploadResponsePayload(payload.sceneId(), "error"));
        }
    }

    private static String computeServerSceneHash(net.minecraft.server.MinecraftServer server, String sceneId) {
        ResourceLocation loc = ResourceLocation.tryParse(sceneId);
        if (loc == null) return "";
        java.nio.file.Path sceneDir = SceneStore.getServerSceneDir(server);
        java.nio.file.Path path = loc.getNamespace().equals(Ponderer.MODID)
            ? sceneDir.resolve(loc.getPath() + ".json")
            : sceneDir.resolve(loc.getNamespace()).resolve(loc.getPath() + ".json");
        if (!java.nio.file.Files.exists(path)) return "";
        try {
            return com.nododiiiii.ponderer.ponder.SyncMeta.sha256(java.nio.file.Files.readAllBytes(path));
        } catch (Exception e) {
            return "";
        }
    }
}