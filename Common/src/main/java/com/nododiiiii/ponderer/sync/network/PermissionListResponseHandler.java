package com.nododiiiii.ponderer.sync.network;

import com.nododiiiii.ponderer.feedback.FeedbackLevel;
import com.nododiiiii.ponderer.feedback.ClientFeedback;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.sync.server.UploadPermissions;
import com.nododiiiii.ponderer.editor.screen.PermissionManagementScreen;
import com.nododiiiii.ponderer.editor.screen.UIText;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;
import com.nododiiiii.ponderer.sync.protocol.PermissionListResponsePayload;
import static com.nododiiiii.ponderer.sync.protocol.PermissionListResponsePayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class PermissionListResponseHandler {
    private PermissionListResponseHandler() {}

    public static void sendSnapshot(ServerPlayer player, String messageKey, String messageSubject, boolean error) {
        UploadPermissions.Snapshot snapshot = UploadPermissions.snapshotFor(player);
        List<Entry> entries = new ArrayList<>();
        for (UploadPermissions.Entry entry : snapshot.entries()) {
            entries.add(new Entry(entry.subject(), entry.role().id(), entry.operatorManaged()));
        }
        UploadPermissions.Role viewerRole = snapshot.viewerRole();
        PondererServices.NETWORK.sendToPlayer(player, new PermissionListResponsePayload(
            List.copyOf(entries),
            viewerRole == null ? "" : viewerRole.id(),
            snapshot.serverOperator(),
            snapshot.canManage(),
            messageKey == null ? "" : messageKey,
            messageSubject == null ? "" : messageSubject,
            error));
    }

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
