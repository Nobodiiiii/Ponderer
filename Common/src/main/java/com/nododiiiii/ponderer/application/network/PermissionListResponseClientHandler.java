package com.nododiiiii.ponderer.application.network;

import com.nododiiiii.ponderer.feedback.FeedbackLevel;
import com.nododiiiii.ponderer.feedback.ClientFeedback;
import com.nododiiiii.ponderer.application.ui.PermissionManagementScreen;
import com.nododiiiii.ponderer.support.ui.UIText;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.nododiiiii.ponderer.sync.protocol.PermissionListResponsePayload;
import static com.nododiiiii.ponderer.sync.protocol.PermissionListResponsePayload.*;

/** Client UI routing is a product adapter, separate from feature processing. */
public final class PermissionListResponseClientHandler {
    private PermissionListResponseClientHandler() {}

    public static void handle(PermissionListResponsePayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (ClientFeedback.responseScreen() instanceof PermissionManagementScreen screen) {
            screen.receiveSnapshot(payload);
            return;
        }

        if (payload.messageKey() != null && !payload.messageKey().isBlank() && client.player != null) {
            String text = payload.messageSubject() == null || payload.messageSubject().isBlank()
                ? UIText.of(payload.messageKey())
                : UIText.of(payload.messageKey(), payload.messageSubject());
            ClientFeedback.result(Component.literal(text),
                payload.error() ? FeedbackLevel.ERROR : FeedbackLevel.SUCCESS);
        }
    }
}
