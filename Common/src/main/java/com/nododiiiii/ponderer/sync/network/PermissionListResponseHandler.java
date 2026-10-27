package com.nododiiiii.ponderer.sync.network;

import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.application.security.UploadPermissions;
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


}
