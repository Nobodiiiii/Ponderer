package com.nododiiiii.ponderer.application.network;

import com.nododiiiii.ponderer.feedback.WorldFeedback;
import com.nododiiiii.ponderer.feedback.ClientFeedback;
import com.nododiiiii.ponderer.feedback.protocol.FeedbackMessagePayload;
import static com.nododiiiii.ponderer.feedback.protocol.FeedbackMessagePayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class FeedbackMessageHandler {
    private FeedbackMessageHandler() {}

    public static void handle(FeedbackMessagePayload payload) {
        if (payload.interaction()) WorldFeedback.interaction(payload.message().copy().withStyle(net.minecraft.ChatFormatting.RED));
        else ClientFeedback.result(payload.message(), payload.level());
    }
}
