package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.RemoteWorkspaceService;
import com.nododiiiii.ponderer.ponder.UploadPermissions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record RemotePullRequestPayload(String kind, String id, @Nullable String pack, boolean includeDependencies) implements CustomPacketPayload {

    public static final Type<RemotePullRequestPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "remote_pull_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RemotePullRequestPayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), RemotePullRequestPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static final int MAX_SYNC_PAYLOAD_BYTES = 900 * 1024;

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(kind());
        buf.writeUtf(id());
        writeOptionalUtf(buf, pack());
        buf.writeBoolean(includeDependencies());
    }

    public static RemotePullRequestPayload decode(RegistryFriendlyByteBuf buf) {
        return new RemotePullRequestPayload(buf.readUtf(), buf.readUtf(), readOptionalUtf(buf), buf.readBoolean());
    }

    public static void handle(RemotePullRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        UploadPermissions.ensurePullAccess(player);
        if (!UploadPermissions.canPull(player)) {
            PondererServices.NETWORK.sendToPlayer(player,
                new RemoteActionResponsePayload(false, "You do not have permission to pull remote Ponderer resources.", false));
            return;
        }

        RemoteWorkspaceService.PullBundle bundle = RemoteWorkspaceService.collectPullBundle(
            player.server, payload.kind(), payload.id(), payload.pack(), payload.includeDependencies());
        if (bundle.scripts().isEmpty() && bundle.structures().isEmpty()) {
            PondererServices.NETWORK.sendToPlayer(player,
                new RemoteActionResponsePayload(false, bundle.message(), false));
            return;
        }

        sendBatched(player, bundle);
        PondererServices.NETWORK.sendToPlayer(player, new RemoteActionResponsePayload(true, bundle.message(), false));
    }

    private static void sendBatched(ServerPlayer player, RemoteWorkspaceService.PullBundle bundle) {
        List<SyncResponsePayload.FileEntry> scriptBatch = new ArrayList<>();
        List<SyncResponsePayload.FileEntry> structureBatch = new ArrayList<>();
        int currentBytes = 0;

        for (SyncResponsePayload.FileEntry entry : bundle.scripts()) {
            currentBytes = queueEntry(player, entry, scriptBatch, structureBatch, true, currentBytes);
        }
        for (SyncResponsePayload.FileEntry entry : bundle.structures()) {
            currentBytes = queueEntry(player, entry, scriptBatch, structureBatch, false, currentBytes);
        }

        PondererServices.NETWORK.sendToPlayer(player,
            new SyncResponsePayload(List.copyOf(scriptBatch), List.copyOf(structureBatch), true, 0));
    }

    private static int queueEntry(ServerPlayer player, SyncResponsePayload.FileEntry entry,
                                  List<SyncResponsePayload.FileEntry> scripts,
                                  List<SyncResponsePayload.FileEntry> structures,
                                  boolean script, int currentBytes) {
        int entrySize = entry.bytes() == null ? 0 : entry.bytes().length;
        if (currentBytes > 0 && currentBytes + entrySize > MAX_SYNC_PAYLOAD_BYTES) {
            PondererServices.NETWORK.sendToPlayer(player,
                new SyncResponsePayload(List.copyOf(scripts), List.copyOf(structures), false, 0));
            scripts.clear();
            structures.clear();
            currentBytes = 0;
        }
        if (script) {
            scripts.add(entry);
        } else {
            structures.add(entry);
        }
        return currentBytes + entrySize;
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
