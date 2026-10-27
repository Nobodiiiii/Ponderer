package com.nododiiiii.ponderer.editor.capture.network;

import com.nododiiiii.ponderer.support.world.RaycastHelper;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.application.security.UploadPermissions;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import com.nododiiiii.ponderer.editor.capture.protocol.CaptureBlockEntityNbtResponsePayload;
import com.nododiiiii.ponderer.editor.capture.protocol.CaptureBlockEntityNbtRequestPayload;
import static com.nododiiiii.ponderer.editor.capture.protocol.CaptureBlockEntityNbtRequestPayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class CaptureBlockEntityNbtRequestHandler {
    private CaptureBlockEntityNbtRequestHandler() {}

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
                nbt = blockEntity.saveWithoutMetadata(player.serverLevel().registryAccess());
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
