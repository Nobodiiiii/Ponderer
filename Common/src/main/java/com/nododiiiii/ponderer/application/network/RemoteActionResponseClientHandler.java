package com.nododiiiii.ponderer.application.network;

import com.nododiiiii.ponderer.feedback.FeedbackLevel;
import com.nododiiiii.ponderer.feedback.ClientFeedback;
import com.nododiiiii.ponderer.application.ui.ProjectorConfigScreen;
import com.nododiiiii.ponderer.application.ui.RemoteBrowserScreen;
import net.minecraft.client.Minecraft;
import com.nododiiiii.ponderer.sync.protocol.RemoteActionResponsePayload;
import static com.nododiiiii.ponderer.sync.protocol.RemoteActionResponsePayload.*;

/** Client UI routing is a product adapter, separate from feature processing. */
public final class RemoteActionResponseClientHandler {
    private RemoteActionResponseClientHandler() {}

    public static void handle(RemoteActionResponsePayload payload) {
        Minecraft client = Minecraft.getInstance();
        ClientFeedback.result(payload.message(), payload.success() ? FeedbackLevel.SUCCESS : FeedbackLevel.ERROR);
        if (ClientFeedback.responseScreen() instanceof RemoteBrowserScreen screen && client.screen == screen) {
            screen.receiveAction(payload);
        } else if (ClientFeedback.responseScreen() instanceof ProjectorConfigScreen screen && client.screen == screen) {
            screen.receiveRemoteAction(payload);
        }
    }
}
