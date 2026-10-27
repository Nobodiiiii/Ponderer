package com.nododiiiii.ponderer.application.network;

import com.nododiiiii.ponderer.application.ui.RemoteBrowserScreen;
import net.minecraft.client.Minecraft;
import com.nododiiiii.ponderer.sync.protocol.RemoteHistoryResponsePayload;
import static com.nododiiiii.ponderer.sync.protocol.RemoteHistoryResponsePayload.*;

/** Client UI routing is a product adapter, separate from feature processing. */
public final class RemoteHistoryResponseClientHandler {
    private RemoteHistoryResponseClientHandler() {}

    public static void handle(RemoteHistoryResponsePayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (com.nododiiiii.ponderer.feedback.ClientFeedback.responseScreen() instanceof RemoteBrowserScreen screen) {
            screen.receiveHistory(payload);
        }
    }
}
