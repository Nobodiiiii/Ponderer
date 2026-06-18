package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.platform.PondererServices;

import com.nododiiiii.ponderer.ponder.RemoteWorkspaceService;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ponder.SyncMeta;
import com.nododiiiii.ponderer.ponder.UploadPermissions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record UploadScenePayload(String sceneId, @Nullable String pack, String json,
                                 List<StructureEntry> structures,
                                 String mode, String lastSyncHash) implements CustomPacketPayload {

    public static final Type<UploadScenePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "upload_scene"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UploadScenePayload> CODEC =
            StreamCodec.of(UploadScenePayload::encode, UploadScenePayload::decode);

    public record StructureEntry(String id, @Nullable String pack, byte[] bytes) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, UploadScenePayload payload) {
        buf.writeUtf(payload.sceneId());
        writeOptionalUtf(buf, payload.pack());
        buf.writeUtf(payload.json());
        buf.writeVarInt(payload.structures().size());
        for (StructureEntry entry : payload.structures()) {
            buf.writeUtf(entry.id());
            writeOptionalUtf(buf, entry.pack());
            buf.writeByteArray(entry.bytes());
        }
        buf.writeUtf(payload.mode() == null ? "check" : payload.mode());
        buf.writeUtf(payload.lastSyncHash() == null ? "" : payload.lastSyncHash());
    }

    private static UploadScenePayload decode(RegistryFriendlyByteBuf buf) {
        String sceneId = buf.readUtf();
        String pack = readOptionalUtf(buf);
        String json = buf.readUtf();
        int size = buf.readVarInt();
        List<StructureEntry> structures = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            structures.add(new StructureEntry(buf.readUtf(), readOptionalUtf(buf), buf.readByteArray()));
        }
        String mode = buf.readUtf();
        String lastSyncHash = buf.readUtf();
        return new UploadScenePayload(sceneId, pack, json, structures, mode, lastSyncHash);
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
        String displayId = SceneStore.displaySceneKey(payload.sceneId(), payload.pack());

        if (!"force".equals(pushMode)) {
            String lastSyncHash = payload.lastSyncHash() == null ? "" : payload.lastSyncHash();
            String serverHash = computeServerSceneHash(player.server, payload.sceneId(), payload.pack());

            if (!serverHash.isEmpty() && !lastSyncHash.isEmpty() && !serverHash.equals(lastSyncHash)) {
                player.sendSystemMessage(Component.translatable("ponderer.cmd.push.server_conflict", displayId));
                PondererServices.NETWORK.sendToPlayer(player,
                        new UploadResponsePayload(payload.sceneId(), payload.pack(), "conflict"));
                return;
            }
        }

        RemoteWorkspaceService.OperationResult result = RemoteWorkspaceService.uploadScene(player, payload);
        boolean ok = result.success();

        if (ok) {
            player.sendSystemMessage(Component.translatable("ponderer.cmd.push.upload_ok", displayId));
            PondererServices.NETWORK.sendToPlayer(player,
                    new UploadResponsePayload(payload.sceneId(), payload.pack(), "ok:" + result.hash()));
        } else {
            player.sendSystemMessage(Component.translatable("ponderer.cmd.push.upload_failed", displayId));
            PondererServices.NETWORK.sendToPlayer(player,
                    new UploadResponsePayload(payload.sceneId(), payload.pack(), "error"));
        }
    }

    private static String computeServerSceneHash(net.minecraft.server.MinecraftServer server, String sceneId,
            @Nullable String pack) {
        ResourceLocation loc = ResourceLocation.tryParse(sceneId);
        if (loc == null) return "";
        java.nio.file.Path path = SceneStore.resolveServerScenePath(server, loc, pack);
        if (path == null) return "";
        if (!java.nio.file.Files.exists(path)) return "";
        try {
            return SyncMeta.sha256(java.nio.file.Files.readAllBytes(path));
        } catch (Exception e) {
            return "";
        }
    }

    private static void writeOptionalUtf(RegistryFriendlyByteBuf buf, @Nullable String value) {
        boolean present = value != null && !value.isBlank();
        buf.writeBoolean(present);
        if (present) {
            buf.writeUtf(value);
        }
    }

    @Nullable
    private static String readOptionalUtf(RegistryFriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUtf() : null;
    }
}
