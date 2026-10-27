package com.nododiiiii.ponderer.application.network;

import com.nododiiiii.ponderer.feedback.FeedbackLevel;
import com.nododiiiii.ponderer.feedback.ClientFeedback;
import com.nododiiiii.ponderer.application.ui.ProjectorFeatureConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.nododiiiii.ponderer.features.projector.protocol.ProjectorFeatureConfigResponsePayload;
import static com.nododiiiii.ponderer.features.projector.protocol.ProjectorFeatureConfigResponsePayload.*;

/** Client UI routing is a product adapter, separate from feature processing. */
public final class ProjectorFeatureConfigResponseClientHandler {
    private ProjectorFeatureConfigResponseClientHandler() {}

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
}
