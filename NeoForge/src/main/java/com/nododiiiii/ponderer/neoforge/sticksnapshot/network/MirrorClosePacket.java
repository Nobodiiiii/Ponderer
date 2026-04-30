package com.nododiiiii.ponderer.neoforge.sticksnapshot.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.snapshot.ReplayAsyncGuard;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record MirrorClosePacket() implements CustomPacketPayload {
    public static final Type<MirrorClosePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "stick_snapshot_mirror_close"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MirrorClosePacket> CODEC =
            StreamCodec.of(MirrorClosePacket::encode, MirrorClosePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, MirrorClosePacket payload) {
    }

    private static MirrorClosePacket decode(RegistryFriendlyByteBuf buf) {
        return new MirrorClosePacket();
    }

    public static void handle(MirrorClosePacket payload, ServerPlayer player) {
        ReplayAsyncGuard.wrap("packet:MirrorClosePacket", () -> {
            if (player == null) {
                return;
            }

            player.inventoryMenu.broadcastFullState();
        }).run();
    }
}
