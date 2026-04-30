package com.nododiiiii.ponderer.neoforge.sticksnapshot.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.client.MirrorNeoForgeOpenClient;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MirrorNeoForgeOpenPacket(int menuTypeId, int windowId, Component title, byte[] extraData,
        int snapshotStateId, CompoundTag snapshotBlockEntityTag) implements CustomPacketPayload {
    public static final Type<MirrorNeoForgeOpenPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "stick_snapshot_mirror_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MirrorNeoForgeOpenPacket> CODEC =
            StreamCodec.of(MirrorNeoForgeOpenPacket::encode, MirrorNeoForgeOpenPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, MirrorNeoForgeOpenPacket payload) {
        buf.writeVarInt(payload.menuTypeId());
        buf.writeVarInt(payload.windowId());
        ComponentSerialization.STREAM_CODEC.encode(buf, payload.title());
        buf.writeByteArray(payload.extraData());
        buf.writeVarInt(payload.snapshotStateId());
        buf.writeBoolean(payload.snapshotBlockEntityTag() != null);
        if (payload.snapshotBlockEntityTag() != null) {
            buf.writeNbt(payload.snapshotBlockEntityTag());
        }
    }

    private static MirrorNeoForgeOpenPacket decode(RegistryFriendlyByteBuf buf) {
        int menuTypeId = buf.readVarInt();
        int windowId = buf.readVarInt();
        Component title = ComponentSerialization.STREAM_CODEC.decode(buf);
        byte[] extraData = buf.readByteArray(32600);
        int snapshotStateId = buf.readVarInt();
        CompoundTag snapshotBlockEntityTag = buf.readBoolean() ? buf.readNbt() : null;
        return new MirrorNeoForgeOpenPacket(menuTypeId, windowId, title, extraData, snapshotStateId,
                snapshotBlockEntityTag);
    }

    public static void handle(MirrorNeoForgeOpenPacket payload) {
        MirrorNeoForgeOpenClient.open(payload);
    }
}
