package com.nododiiiii.ponderer.neoforge.sticksnapshot.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.snapshot.ItemSnapshot;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.snapshot.ItemSnapshotStorage;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.snapshot.ReplayAsyncGuard;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record SaveItemSnapshotPacket(ItemSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<SaveItemSnapshotPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "stick_snapshot_save_item"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveItemSnapshotPacket> CODEC =
            StreamCodec.of(SaveItemSnapshotPacket::encode, SaveItemSnapshotPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, SaveItemSnapshotPacket payload) {
        payload.snapshot().writeToBuf(buf);
    }

    private static SaveItemSnapshotPacket decode(RegistryFriendlyByteBuf buf) {
        return new SaveItemSnapshotPacket(ItemSnapshot.fromBuf(buf));
    }

    public static void handle(SaveItemSnapshotPacket payload, ServerPlayer player) {
        ReplayAsyncGuard.wrap("packet:SaveItemSnapshotPacket", () -> {
            if (player != null) {
                ItemSnapshotStorage.save(player, payload.snapshot());
            }
        }).run();
    }
}
