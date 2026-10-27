package com.nododiiiii.ponderer.sync.network;

import com.nododiiiii.ponderer.feedback.FeedbackLevel;
import com.nododiiiii.ponderer.feedback.ServerFeedback;
import com.nododiiiii.ponderer.sync.server.UploadPermissions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import com.nododiiiii.ponderer.sync.protocol.SyncRequestPayload;
import static com.nododiiiii.ponderer.sync.protocol.SyncRequestPayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class SyncRequestHandler {
    private SyncRequestHandler() {}

    public static void handle(SyncRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        UploadPermissions.ensurePullAccess(player);
        if (!UploadPermissions.canPull(player)) {
            ServerFeedback.send(player, Component.translatable("ponderer.cmd.pull.no_permission"), FeedbackLevel.ERROR);
            return;
        }
        com.nododiiiii.ponderer.sync.server.SyncSender.sendBatched(player, payload.mode());
    }
}
