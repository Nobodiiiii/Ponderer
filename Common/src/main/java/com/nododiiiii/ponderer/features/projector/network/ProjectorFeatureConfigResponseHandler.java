package com.nododiiiii.ponderer.features.projector.network;

import com.nododiiiii.ponderer.feedback.FeedbackLevel;
import com.nododiiiii.ponderer.feedback.ClientFeedback;
import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.sync.server.UploadPermissions;
import com.nododiiiii.ponderer.editor.screen.ProjectorFeatureConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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

    public static void handle(ProjectorFeatureConfigResponsePayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (ClientFeedback.responseScreen() instanceof ProjectorFeatureConfigScreen screen) {
            screen.receiveServerState(payload);
            return;
        }

        if (payload.messageKey() != null && !payload.messageKey().isBlank() && client.player != null) {
            ClientFeedback.result(Component.translatable(payload.messageKey()),
                payload.error() ? FeedbackLevel.ERROR : FeedbackLevel.SUCCESS);
        }
    }

    private static boolean currentEnableProjector() {
        try {
            return Config.ENABLE_PROJECTOR.get();
        } catch (Exception ignored) {
            return false;
        }
    }
}
