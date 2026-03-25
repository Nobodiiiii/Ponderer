package com.nododiiiii.ponderer.forge.sticksnapshot.network;

import com.nododiiiii.ponderer.forge.sticksnapshot.StickSnapshotFeature;
import com.nododiiiii.ponderer.forge.sticksnapshot.client.MirrorForgeOpenClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record MirrorForgeOpenPacket(int menuTypeId, int windowId, Component title, byte[] extraData,
        int snapshotStateId, CompoundTag snapshotBlockEntityTag) {
    public static void encode(MirrorForgeOpenPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.menuTypeId);
        buf.writeVarInt(msg.windowId);
        buf.writeComponent(msg.title);
        buf.writeByteArray(msg.extraData);
        buf.writeVarInt(msg.snapshotStateId);
        buf.writeBoolean(msg.snapshotBlockEntityTag != null);
        if (msg.snapshotBlockEntityTag != null) {
            buf.writeNbt(msg.snapshotBlockEntityTag);
        }
    }

    public static MirrorForgeOpenPacket decode(FriendlyByteBuf buf) {
        int menuTypeId = buf.readVarInt();
        int windowId = buf.readVarInt();
        Component title = buf.readComponent();
        byte[] extraData = buf.readByteArray(32600);
        int snapshotStateId = buf.readVarInt();
        CompoundTag snapshotBlockEntityTag = buf.readBoolean() ? buf.readNbt() : null;
        return new MirrorForgeOpenPacket(menuTypeId, windowId, title, extraData, snapshotStateId,
                snapshotBlockEntityTag);
    }

    public static void handle(MirrorForgeOpenPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        StickSnapshotFeature.LOGGER.debug(
                "[net][mirror-debug] MirrorForgeOpenPacket recv side={} windowId={} menuTypeId={} title={}",
                ctx.getDirection().getReceptionSide(), msg.windowId(), msg.menuTypeId(), msg.title().getString());
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            StickSnapshotFeature.LOGGER.debug(
                    "[net][mirror-debug] MirrorForgeOpenPacket handling on client windowId={} menuTypeId={}",
                    msg.windowId(), msg.menuTypeId());
            MirrorForgeOpenClient.open(msg);
        }));
        ctx.setPacketHandled(true);
    }
}
