package com.nododiiiii.ponderer.application.network;
import com.nododiiiii.ponderer.application.network.FeedbackRequestDispatch;

import com.nododiiiii.ponderer.feedback.ServerFeedback;
import net.minecraft.server.level.ServerPlayer;
import com.nododiiiii.ponderer.feedback.protocol.FeedbackRequestPayload;
import static com.nododiiiii.ponderer.feedback.protocol.FeedbackRequestPayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class FeedbackRequestHandler {
    private FeedbackRequestHandler() {}

    public static void handle(FeedbackRequestPayload request, ServerPlayer player) {
        ServerFeedback.handle(request.requestId(), player, () -> FeedbackRequestDispatch.handleRequest(request.payload(), player));
    }
}
