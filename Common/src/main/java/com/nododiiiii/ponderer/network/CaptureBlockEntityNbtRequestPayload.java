package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.blueprint.RaycastHelper;
import com.nododiiiii.ponderer.ponder.UploadPermissions;
import com.nododiiiii.ponderer.platform.PondererServices;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
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
        if (UploadPermissions.canUpload(player)
                && isAuthorizedBlockCapture(player, payload.pos())
                && player.serverLevel().hasChunkAt(payload.pos())) {
            BlockEntity blockEntity = player.serverLevel().getBlockEntity(payload.pos());
            if (blockEntity != null) {
                nbt = blockEntity.saveWithoutMetadata();
            }
        }

        PondererServices.NETWORK.sendToPlayer(player, new CaptureBlockEntityNbtResponsePayload(payload.pos(), nbt));
    }

    private static boolean isAuthorizedBlockCapture(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(pos.getCenter()) > 36.0D) {
            return false;
        }
        BlockHitResult hit = RaycastHelper.rayTraceRange(player.serverLevel(), player, 6.0D);
        return hit != null && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                && pos.equals(hit.getBlockPos());
    }
}
