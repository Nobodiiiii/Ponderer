package com.nododiiiii.ponderer.forge.sticksnapshot.network;

import com.nododiiiii.ponderer.forge.sticksnapshot.snapshot.ItemSnapshot;
import com.nododiiiii.ponderer.forge.sticksnapshot.snapshot.ReplayAsyncGuard;
import com.nododiiiii.ponderer.forge.sticksnapshot.snapshot.ItemSnapshotStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SaveItemSnapshotPacket {
    private final ItemSnapshot snapshot;

    public SaveItemSnapshotPacket(ItemSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public static void encode(SaveItemSnapshotPacket msg, FriendlyByteBuf buf) {
        msg.snapshot.writeToBuf(buf);
    }

    public static SaveItemSnapshotPacket decode(FriendlyByteBuf buf) {
        return new SaveItemSnapshotPacket(ItemSnapshot.fromBuf(buf));
    }

    public static void handle(SaveItemSnapshotPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(ReplayAsyncGuard.wrap("packet:SaveItemSnapshotPacket", () -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                ItemSnapshotStorage.save(player, msg.snapshot);
            }
        }));
        ctx.setPacketHandled(true);
    }
}
