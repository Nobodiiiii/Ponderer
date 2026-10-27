package com.nododiiiii.ponderer.features.blueprint.network;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.application.security.UploadPermissions;
import net.minecraft.server.level.ServerPlayer;
import com.nododiiiii.ponderer.features.blueprint.protocol.BlueprintConfigResponsePayload;
import static com.nododiiiii.ponderer.features.blueprint.protocol.BlueprintConfigResponsePayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class BlueprintConfigResponseHandler {
    private BlueprintConfigResponseHandler() {}

    public static void sendCurrentState(ServerPlayer player, String messageKey, boolean error) {
        PondererServices.NETWORK.sendToPlayer(player, new BlueprintConfigResponsePayload(
            currentEnableBuiltinItem(),
            UploadPermissions.canManage(player),
            messageKey == null ? "" : messageKey,
            error));
    }

    private static boolean currentEnableBuiltinItem() {
        try {
            return Config.ENABLE_BLUEPRINT_ITEM.get();
        } catch (Exception ignored) {
            return false;
        }
    }
}
