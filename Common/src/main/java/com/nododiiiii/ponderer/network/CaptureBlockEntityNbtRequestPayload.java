package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.platform.PondererServices;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public record CaptureBlockEntityNbtRequestPayload(BlockPos pos) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos());
    }

    public static CaptureBlockEntityNbtRequestPayload decode(FriendlyByteBuf buf) {
        return new CaptureBlockEntityNbtRequestPayload(buf.readBlockPos());
    }

    public static void handle(CaptureBlockEntityNbtRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }

        CompoundTag nbt = null;
        if (player.serverLevel().hasChunkAt(payload.pos())) {
            BlockEntity blockEntity = player.serverLevel().getBlockEntity(payload.pos());
            if (blockEntity != null) {
                nbt = blockEntity.saveWithFullMetadata();
            }
        }

        PondererServices.NETWORK.sendToPlayer(player, new CaptureBlockEntityNbtResponsePayload(payload.pos(), nbt));
    }
}
