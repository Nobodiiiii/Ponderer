package com.nododiiiii.ponderer.forge.sticksnapshot.network;

import com.nododiiiii.ponderer.forge.sticksnapshot.StickSnapshotFeature;
import com.nododiiiii.ponderer.forge.sticksnapshot.snapshot.ItemSnapshot;
import com.nododiiiii.ponderer.forge.sticksnapshot.snapshot.ItemSnapshotStorage;
import com.nododiiiii.ponderer.forge.sticksnapshot.snapshot.ReplayAsyncGuard;
import com.nododiiiii.ponderer.forge.sticksnapshot.snapshot.SnapshotReplayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ReplayItemSnapshotPacket {
    public static void encode(ReplayItemSnapshotPacket msg, FriendlyByteBuf buf) {
    }

    public static ReplayItemSnapshotPacket decode(FriendlyByteBuf buf) {
        return new ReplayItemSnapshotPacket();
    }

    public static void handle(ReplayItemSnapshotPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(ReplayAsyncGuard.wrap("packet:ReplayItemSnapshotPacket", () -> {
            ServerPlayer player = ctx.getSender();
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
        }));
        ctx.setPacketHandled(true);
    }
}
