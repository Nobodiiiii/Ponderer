package com.nododiiiii.ponderer.features.projector.network;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.application.security.UploadPermissions;
import net.minecraft.server.level.ServerPlayer;
import com.nododiiiii.ponderer.features.projector.protocol.ProjectorFeatureConfigResponsePayload;
import static com.nododiiiii.ponderer.features.projector.protocol.ProjectorFeatureConfigResponsePayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class ProjectorFeatureConfigResponseHandler {
    private ProjectorFeatureConfigResponseHandler() {}

    public static void sendCurrentState(ServerPlayer player, String messageKey, boolean error) {
        PondererServices.NETWORK.sendToPlayer(player, new ProjectorFeatureConfigResponsePayload(
            currentEnableProjector(),
            UploadPermissions.canManage(player),
            messageKey == null ? "" : messageKey,
            error));
    }

    private static boolean currentEnableProjector() {
        try {
            return Config.ENABLE_PROJECTOR.get();
        } catch (Exception ignored) {
            return false;
        }
    }
}
