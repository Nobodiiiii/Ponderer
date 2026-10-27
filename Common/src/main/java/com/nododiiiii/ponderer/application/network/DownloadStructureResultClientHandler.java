package com.nododiiiii.ponderer.application.network;

import com.nododiiiii.ponderer.feedback.FeedbackLevel;
import com.nododiiiii.ponderer.feedback.ClientFeedback;
import com.nododiiiii.ponderer.application.ui.ShowExtraStructureScreen;
import com.nododiiiii.ponderer.application.ui.ShowStructureScreen;
import com.nododiiiii.ponderer.sync.protocol.DownloadStructureResultPayload;
import static com.nododiiiii.ponderer.sync.protocol.DownloadStructureResultPayload.*;

/** Client UI routing is a product adapter, separate from feature processing. */
public final class DownloadStructureResultClientHandler {
    private DownloadStructureResultClientHandler() {}

    public static void handle(DownloadStructureResultPayload payload) {
        String message = net.minecraft.network.chat.Component.translatable(payload.message(), payload.sourceId(), payload.targetId()).getString();
        ShowStructureScreen.onDownloadResult(payload.sourceId(), payload.targetId(), payload.success(), message);
        ShowExtraStructureScreen.onDownloadResult(payload.sourceId(), payload.targetId(), payload.success(), message);
        ClientFeedback.result(net.minecraft.network.chat.Component.literal(message),
            payload.success() ? FeedbackLevel.SUCCESS : FeedbackLevel.ERROR);
    }
}
