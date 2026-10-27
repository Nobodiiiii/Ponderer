package com.nododiiiii.ponderer.application.network;

import com.nododiiiii.ponderer.application.ui.StructureListScreen;
import net.minecraft.client.Minecraft;
import com.nododiiiii.ponderer.sync.protocol.StructurePreviewResponsePayload;
import static com.nododiiiii.ponderer.sync.protocol.StructurePreviewResponsePayload.*;

/** Client UI routing is a product adapter, separate from feature processing. */
public final class StructurePreviewResponseClientHandler {
    private StructurePreviewResponseClientHandler() {}

    public static void handle(StructurePreviewResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof StructureListScreen screen) {
            screen.acceptPreviewResponse(payload);
        }
    }
}
