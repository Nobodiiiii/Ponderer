package com.nododiiiii.ponderer.features.projector.network;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import com.nododiiiii.ponderer.features.projector.protocol.ProjectorFeatureConfigRequestPayload;
import static com.nododiiiii.ponderer.features.projector.protocol.ProjectorFeatureConfigRequestPayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class ProjectorFeatureConfigRequestHandler {
    private ProjectorFeatureConfigRequestHandler() {}

    public static void handle(ProjectorFeatureConfigRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        ProjectorFeatureConfigResponseHandler.sendCurrentState(player, "", false);
    }
}
