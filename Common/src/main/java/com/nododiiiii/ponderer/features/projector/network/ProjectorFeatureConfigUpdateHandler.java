package com.nododiiiii.ponderer.features.projector.network;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.sync.server.UploadPermissions;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import com.nododiiiii.ponderer.features.projector.network.ProjectorFeatureConfigResponseHandler;
import com.nododiiiii.ponderer.features.projector.protocol.ProjectorFeatureConfigUpdatePayload;
import static com.nododiiiii.ponderer.features.projector.protocol.ProjectorFeatureConfigUpdatePayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class ProjectorFeatureConfigUpdateHandler {
    private ProjectorFeatureConfigUpdateHandler() {}

    private static final Logger LOGGER = LogUtils.getLogger();


    public static void handle(ProjectorFeatureConfigUpdatePayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }

        if (!UploadPermissions.canManage(player)) {
            ProjectorFeatureConfigResponseHandler.sendCurrentState(
                player,
                "ponderer.ui.function_page.projector.admin_required",
                true);
            return;
        }

        try {
            Config.ENABLE_PROJECTOR.set(payload.enableProjector());
            Config.SERVER_SPEC.save();
            ProjectorFeatureConfigResponseHandler.sendCurrentState(
                player,
                payload.enableProjector()
                    ? "ponderer.ui.function_page.projector.saved.enabled"
                    : "ponderer.ui.function_page.projector.saved.disabled",
                false);
        } catch (Exception e) {
            LOGGER.warn("Failed to update Ponderer projector server config", e);
            ProjectorFeatureConfigResponseHandler.sendCurrentState(
                player,
                "ponderer.ui.function_page.projector.error",
                true);
        }
    }
}
