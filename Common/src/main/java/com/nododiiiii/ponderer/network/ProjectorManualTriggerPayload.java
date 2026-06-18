package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.projector.ProjectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public record ProjectorManualTriggerPayload(BlockPos projectorPos) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(projectorPos);
    }

    public static ProjectorManualTriggerPayload decode(FriendlyByteBuf buf) {
        return new ProjectorManualTriggerPayload(buf.readBlockPos());
    }

    public static void handle(ProjectorManualTriggerPayload payload, @Nullable ServerPlayer player) {
        if (player == null
            || !player.serverLevel().hasChunkAt(payload.projectorPos())
            || player.distanceToSqr(payload.projectorPos().getCenter()) > 64.0D) {
            return;
        }
        if (player.serverLevel().getBlockEntity(payload.projectorPos()) instanceof ProjectorBlockEntity projector) {
            projector.triggerManualOnce();
        }
    }
}
