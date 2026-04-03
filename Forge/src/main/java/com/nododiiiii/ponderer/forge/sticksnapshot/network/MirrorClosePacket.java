package com.nododiiiii.ponderer.forge.sticksnapshot.network;

import com.nododiiiii.ponderer.forge.sticksnapshot.snapshot.ReplayAsyncGuard;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MirrorClosePacket {
    public static void encode(MirrorClosePacket msg, FriendlyByteBuf buf) {
    }

    public static MirrorClosePacket decode(FriendlyByteBuf buf) {
        return new MirrorClosePacket();
    }

    public static void handle(MirrorClosePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(ReplayAsyncGuard.wrap("packet:MirrorClosePacket", () -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }

            player.inventoryMenu.broadcastFullState();
        }));
        ctx.setPacketHandled(true);
    }
}
