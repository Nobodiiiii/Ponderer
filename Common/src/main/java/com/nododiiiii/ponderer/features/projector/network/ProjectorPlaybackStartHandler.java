package com.nododiiiii.ponderer.features.projector.network;

import com.nododiiiii.ponderer.features.projector.ProjectorBlockEntity;
import net.minecraft.client.Minecraft;
import com.nododiiiii.ponderer.features.projector.protocol.ProjectorPlaybackStartPayload;
import static com.nododiiiii.ponderer.features.projector.protocol.ProjectorPlaybackStartPayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class ProjectorPlaybackStartHandler {
    private ProjectorPlaybackStartHandler() {}

    public static void handle(ProjectorPlaybackStartPayload payload) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        if (Minecraft.getInstance().level.getBlockEntity(payload.projectorPos()) instanceof ProjectorBlockEntity projector) {
            projector.startClientPlaybackFromServerSignal(payload.looping());
        }
    }
}
