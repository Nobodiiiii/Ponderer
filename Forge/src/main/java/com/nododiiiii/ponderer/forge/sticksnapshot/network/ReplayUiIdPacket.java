package com.nododiiiii.ponderer.forge.sticksnapshot.network;

import com.nododiiiii.ponderer.forge.sticksnapshot.StickSnapshotFeature;
import com.nododiiiii.ponderer.forge.sticksnapshot.snapshot.ReplayAsyncGuard;
import com.nododiiiii.ponderer.forge.sticksnapshot.snapshot.SnapshotReplayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ReplayUiIdPacket {
    private final ResourceLocation menuTypeId;

    public ReplayUiIdPacket(ResourceLocation menuTypeId) {
        this.menuTypeId = menuTypeId;
    }

    public static void encode(ReplayUiIdPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.menuTypeId);
    }

    public static ReplayUiIdPacket decode(FriendlyByteBuf buf) {
        return new ReplayUiIdPacket(buf.readResourceLocation());
    }

    public static void handle(ReplayUiIdPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(ReplayAsyncGuard.wrap("packet:ReplayUiIdPacket", () -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            try {
                SnapshotReplayer.replayUiId(player, msg.menuTypeId);
            } catch (Exception ex) {
                StickSnapshotFeature.LOGGER.warn("replayUiId threw for player={} menu={}",
                        player.getScoreboardName(), msg.menuTypeId, ex);
            }
        }));
        ctx.setPacketHandled(true);
    }
}
