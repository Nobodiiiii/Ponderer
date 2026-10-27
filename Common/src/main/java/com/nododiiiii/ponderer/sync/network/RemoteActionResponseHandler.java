package com.nododiiiii.ponderer.sync.network;

import com.nododiiiii.ponderer.feedback.FeedbackLevel;
import com.nododiiiii.ponderer.feedback.ClientFeedback;
import com.nododiiiii.ponderer.editor.screen.ProjectorConfigScreen;
import com.nododiiiii.ponderer.editor.screen.RemoteBrowserScreen;
import net.minecraft.client.Minecraft;
import com.nododiiiii.ponderer.sync.protocol.RemoteActionResponsePayload;
import static com.nododiiiii.ponderer.sync.protocol.RemoteActionResponsePayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class RemoteActionResponseHandler {
    private RemoteActionResponseHandler() {}

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
