package com.nododiiiii.ponderer.features.blueprint.network;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.application.security.UploadPermissions;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import com.nododiiiii.ponderer.features.blueprint.protocol.BlueprintConfigUpdatePayload;
import static com.nododiiiii.ponderer.features.blueprint.protocol.BlueprintConfigUpdatePayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class BlueprintConfigUpdateHandler {
    private BlueprintConfigUpdateHandler() {}

    private static final Logger LOGGER = LogUtils.getLogger();


    public static void handle(BlueprintConfigUpdatePayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }

        if (!UploadPermissions.canManage(player)) {
            BlueprintConfigResponseHandler.sendCurrentState(
                player,
                "ponderer.ui.function_page.blueprint_item.admin_required",
                true);
            return;
        }

        try {
            Config.ENABLE_BLUEPRINT_ITEM.set(payload.enableBuiltinItem());
            Config.SERVER_SPEC.save();
            BlueprintConfigResponseHandler.sendCurrentState(
                player,
                payload.enableBuiltinItem()
                    ? "ponderer.ui.function_page.blueprint_item.saved.enabled"
                    : "ponderer.ui.function_page.blueprint_item.saved.disabled",
                false);
        } catch (Exception e) {
            LOGGER.warn("Failed to update Ponderer blueprint server config", e);
            BlueprintConfigResponseHandler.sendCurrentState(
                player,
                "ponderer.ui.function_page.blueprint_item.error",
                true);
        }
    }
}
