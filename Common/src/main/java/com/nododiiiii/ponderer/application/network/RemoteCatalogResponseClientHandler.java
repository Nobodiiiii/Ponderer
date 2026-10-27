package com.nododiiiii.ponderer.application.network;

import com.nododiiiii.ponderer.application.ui.RemoteBrowserScreen;
import net.minecraft.client.Minecraft;
import com.nododiiiii.ponderer.sync.protocol.RemoteCatalogResponsePayload;
import static com.nododiiiii.ponderer.sync.protocol.RemoteCatalogResponsePayload.*;

/** Client UI routing is a product adapter, separate from feature processing. */
public final class RemoteCatalogResponseClientHandler {
    private RemoteCatalogResponseClientHandler() {}

    public static void handle(RemoteCatalogResponsePayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (com.nododiiiii.ponderer.feedback.ClientFeedback.responseScreen() instanceof RemoteBrowserScreen screen) {
            screen.receiveCatalog(payload);
        }
    }
}
