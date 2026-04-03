package com.nododiiiii.ponderer.forge.sticksnapshot.network;

import com.nododiiiii.ponderer.forge.sticksnapshot.StickSnapshotFeature;
import com.nododiiiii.ponderer.forge.sticksnapshot.snapshot.ReplayAsyncGuard;
import com.nododiiiii.ponderer.forge.sticksnapshot.snapshot.BlockSnapshot;
import com.nododiiiii.ponderer.forge.sticksnapshot.snapshot.SnapshotReplayer;
import com.nododiiiii.ponderer.forge.sticksnapshot.snapshot.SnapshotStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ReplaySnapshotPacket {
    public static void encode(ReplaySnapshotPacket msg, FriendlyByteBuf buf) {
    }

    public static ReplaySnapshotPacket decode(FriendlyByteBuf buf) {
        return new ReplaySnapshotPacket();
    }

    public static void handle(ReplaySnapshotPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(ReplayAsyncGuard.wrap("packet:ReplaySnapshotPacket", () -> {
            ServerPlayer player = ctx.getSender();
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
        }));
        ctx.setPacketHandled(true);
    }
}
