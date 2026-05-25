package com.nododiiiii.ponderer.neoforge.sticksnapshot.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.StickSnapshotFeature;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.snapshot.ItemSnapshot;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.snapshot.ItemSnapshotStorage;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.snapshot.ReplayAsyncGuard;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.snapshot.SnapshotReplayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record ReplayItemSnapshotPacket() implements CustomPacketPayload {
    public static final Type<ReplayItemSnapshotPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "stick_snapshot_replay_item"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReplayItemSnapshotPacket> CODEC =
            StreamCodec.of(ReplayItemSnapshotPacket::encode, ReplayItemSnapshotPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ReplayItemSnapshotPacket payload) {
    }

    private static ReplayItemSnapshotPacket decode(RegistryFriendlyByteBuf buf) {
        return new ReplayItemSnapshotPacket();
    }

    public static void handle(ReplayItemSnapshotPacket payload, ServerPlayer player) {
        ReplayAsyncGuard.wrap("packet:ReplayItemSnapshotPacket", () -> {
            if (player == null) {
                return;
            }
            ItemSnapshot snapshot = ItemSnapshotStorage.load(player);
            if (snapshot != null) {
                SnapshotReplayer.replayItem(player, snapshot);
            } else {
                StickSnapshotFeature.LOGGER.warn("item replay skipped: no item snapshot saved for player={}",
                        player.getScoreboardName());
            }
        }).run();
    }
}
