package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.projector.ProjectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public record ProjectorSeekPayload(BlockPos projectorPos, int playbackTick) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(projectorPos);
        buf.writeVarInt(Math.max(0, playbackTick));
    }

    public static ProjectorSeekPayload decode(FriendlyByteBuf buf) {
        return new ProjectorSeekPayload(buf.readBlockPos(), buf.readVarInt());
    }

    public static void handle(ProjectorSeekPayload payload, @Nullable ServerPlayer player) {
        if (player == null
            || !player.serverLevel().hasChunkAt(payload.projectorPos())
            || player.distanceToSqr(payload.projectorPos().getCenter()) > 64.0D) {
            return;
        }
        if (player.serverLevel().getBlockEntity(payload.projectorPos()) instanceof ProjectorBlockEntity projector) {
            projector.seekPlaybackToTick(payload.playbackTick());
        }
    }
}
