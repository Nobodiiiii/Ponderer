package com.nododiiiii.ponderer.neoforge.sticksnapshot.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.StickSnapshotFeature;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.snapshot.BlockSnapshot;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.snapshot.ReplayAsyncGuard;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.snapshot.SnapshotReplayer;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.snapshot.SnapshotStorage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record ReplaySnapshotPacket() implements CustomPacketPayload {
    public static final Type<ReplaySnapshotPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "stick_snapshot_replay"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReplaySnapshotPacket> CODEC =
            StreamCodec.of(ReplaySnapshotPacket::encode, ReplaySnapshotPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ReplaySnapshotPacket payload) {
    }

    private static ReplaySnapshotPacket decode(RegistryFriendlyByteBuf buf) {
        return new ReplaySnapshotPacket();
    }

    public static void handle(ReplaySnapshotPacket payload, ServerPlayer player) {
        ReplayAsyncGuard.wrap("packet:ReplaySnapshotPacket", () -> {
            if (player == null) {
                return;
            }
            BlockSnapshot snapshot = SnapshotStorage.load(player);
            if (snapshot != null) {
                SnapshotReplayer.replay(player, snapshot);
            } else {
                StickSnapshotFeature.LOGGER.warn("stick replay skipped: no snapshot saved for player={}",
                        player.getScoreboardName());
            }
        }).run();
    }
}
