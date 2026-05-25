package com.nododiiiii.ponderer.neoforge.sticksnapshot.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.StickSnapshotFeature;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.snapshot.ReplayAsyncGuard;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.snapshot.SnapshotReplayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record ReplayUiIdPacket(ResourceLocation menuTypeId) implements CustomPacketPayload {
    public static final Type<ReplayUiIdPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "stick_snapshot_replay_ui_id"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReplayUiIdPacket> CODEC =
            StreamCodec.of(ReplayUiIdPacket::encode, ReplayUiIdPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ReplayUiIdPacket payload) {
        buf.writeResourceLocation(payload.menuTypeId());
    }

    private static ReplayUiIdPacket decode(RegistryFriendlyByteBuf buf) {
        return new ReplayUiIdPacket(buf.readResourceLocation());
    }

    public static void handle(ReplayUiIdPacket payload, ServerPlayer player) {
        ReplayAsyncGuard.wrap("packet:ReplayUiIdPacket", () -> {
            if (player == null) {
                return;
            }
            try {
                SnapshotReplayer.replayUiId(player, payload.menuTypeId());
            } catch (Exception ex) {
                StickSnapshotFeature.LOGGER.warn("replayUiId threw for player={} menu={}",
                        player.getScoreboardName(), payload.menuTypeId(), ex);
            }
        }).run();
    }
}
