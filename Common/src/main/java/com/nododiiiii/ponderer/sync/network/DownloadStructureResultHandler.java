package com.nododiiiii.ponderer.sync.network;

import com.nododiiiii.ponderer.feedback.FeedbackLevel;
import com.nododiiiii.ponderer.feedback.ClientFeedback;
import com.nododiiiii.ponderer.editor.screen.ShowExtraStructureScreen;
import com.nododiiiii.ponderer.editor.screen.ShowStructureScreen;
import com.nododiiiii.ponderer.sync.protocol.DownloadStructureResultPayload;
import static com.nododiiiii.ponderer.sync.protocol.DownloadStructureResultPayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class DownloadStructureResultHandler {
    private DownloadStructureResultHandler() {}

    public static void handle(DownloadStructureResultPayload payload) {
        String message = net.minecraft.network.chat.Component.translatable(payload.message(), payload.sourceId(), payload.targetId()).getString();
        ShowStructureScreen.onDownloadResult(payload.sourceId(), payload.targetId(), payload.success(), message);
        ShowExtraStructureScreen.onDownloadResult(payload.sourceId(), payload.targetId(), payload.success(), message);
        ClientFeedback.result(net.minecraft.network.chat.Component.literal(message),
            payload.success() ? FeedbackLevel.SUCCESS : FeedbackLevel.ERROR);
    }
}
