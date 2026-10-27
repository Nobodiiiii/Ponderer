package com.nododiiiii.ponderer.features.projector.network;

import com.nododiiiii.ponderer.features.projector.ProjectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import com.nododiiiii.ponderer.features.projector.protocol.ProjectorConfigUpdatePayload;
import static com.nododiiiii.ponderer.features.projector.protocol.ProjectorConfigUpdatePayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class ProjectorConfigUpdateHandler {
    private ProjectorConfigUpdateHandler() {}

    public static void handle(ProjectorConfigUpdatePayload payload, @Nullable ServerPlayer player) {
        if (player == null || !isAuthorized(player, payload.projectorPos())) {
            return;
        }
        if (!(player.serverLevel().getBlockEntity(payload.projectorPos()) instanceof ProjectorBlockEntity projector)) {
            return;
        }
        projector.applyConfig(payload.triggerMode(), payload.anchorPos(),
            payload.intermissionTicks(), payload.showBlueTint(),
            payload.overlayAntiOcclusion(), payload.compatibilityMode(), payload.projectionMode(),
            payload.miniatureScale(), payload.textScale());
    }

    private static boolean isAuthorized(ServerPlayer player, BlockPos pos) {
        return player.serverLevel().hasChunkAt(pos) && player.distanceToSqr(pos.getCenter()) <= 64.0D;
    }
}
