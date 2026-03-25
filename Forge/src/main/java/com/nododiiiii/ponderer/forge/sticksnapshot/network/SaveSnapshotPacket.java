package com.nododiiiii.ponderer.forge.sticksnapshot.network;

import com.nododiiiii.ponderer.forge.sticksnapshot.StickSnapshotFeature;
import com.nododiiiii.ponderer.forge.sticksnapshot.snapshot.BlockSnapshot;
import com.nododiiiii.ponderer.forge.sticksnapshot.snapshot.ReplayAsyncGuard;
import com.nododiiiii.ponderer.forge.sticksnapshot.snapshot.SnapshotStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SaveSnapshotPacket {
    private final BlockSnapshot snapshot;

    public SaveSnapshotPacket(BlockSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public static void encode(SaveSnapshotPacket msg, FriendlyByteBuf buf) {
        msg.snapshot.writeToBuf(buf);
    }

    public static SaveSnapshotPacket decode(FriendlyByteBuf buf) {
        return new SaveSnapshotPacket(BlockSnapshot.fromBuf(buf));
    }

    public static void handle(SaveSnapshotPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(ReplayAsyncGuard.wrap("packet:SaveSnapshotPacket", () -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                SnapshotStorage.save(player, msg.snapshot);
                StickSnapshotFeature.LOGGER.debug("[server] snapshot saved for player={} block={} pos={} dim={}",
                        player.getScoreboardName(), msg.snapshot.getBlockId(), msg.snapshot.getPos(), msg.snapshot.getDimensionId());
            }
        }));
        ctx.setPacketHandled(true);
    }
}
