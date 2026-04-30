package com.nododiiiii.ponderer.neoforge.sticksnapshot.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.snapshot.BlockSnapshot;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.snapshot.ReplayAsyncGuard;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.snapshot.SnapshotStorage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record SaveSnapshotPacket(BlockSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<SaveSnapshotPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "stick_snapshot_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveSnapshotPacket> CODEC =
            StreamCodec.of(SaveSnapshotPacket::encode, SaveSnapshotPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, SaveSnapshotPacket payload) {
        payload.snapshot().writeToBuf(buf);
    }

    private static SaveSnapshotPacket decode(RegistryFriendlyByteBuf buf) {
        return new SaveSnapshotPacket(BlockSnapshot.fromBuf(buf));
    }

    public static void handle(SaveSnapshotPacket payload, ServerPlayer player) {
        ReplayAsyncGuard.wrap("packet:SaveSnapshotPacket", () -> {
            if (player != null) {
                SnapshotStorage.save(player, payload.snapshot());
            }
        }).run();
    }
}
