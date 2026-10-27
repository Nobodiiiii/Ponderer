package com.nododiiiii.ponderer.sync.network;

import com.nododiiiii.ponderer.editor.screen.StructureListScreen;
import net.minecraft.client.Minecraft;
import com.nododiiiii.ponderer.sync.protocol.StructurePreviewResponsePayload;
import static com.nododiiiii.ponderer.sync.protocol.StructurePreviewResponsePayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class StructurePreviewResponseHandler {
    private StructurePreviewResponseHandler() {}

    public static void handle(StructurePreviewResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof StructureListScreen screen) {
            screen.acceptPreviewResponse(payload);
        }
    }
}
