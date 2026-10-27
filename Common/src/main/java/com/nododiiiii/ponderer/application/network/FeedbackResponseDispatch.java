package com.nododiiiii.ponderer.application.network;

import com.nododiiiii.ponderer.sync.client.RemotePullClient;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.nododiiiii.ponderer.features.blueprint.protocol.BlueprintConfigResponsePayload;
import com.nododiiiii.ponderer.sync.protocol.DownloadStructureResultPayload;
import com.nododiiiii.ponderer.feedback.protocol.FeedbackMessagePayload;
import com.nododiiiii.ponderer.sync.protocol.PermissionListResponsePayload;
import com.nododiiiii.ponderer.features.projector.protocol.ProjectorFeatureConfigResponsePayload;
import com.nododiiiii.ponderer.sync.protocol.RemoteActionResponsePayload;
import com.nododiiiii.ponderer.sync.protocol.RemoteCatalogResponsePayload;
import com.nododiiiii.ponderer.sync.protocol.RemoteHistoryResponsePayload;
import com.nododiiiii.ponderer.sync.protocol.SyncResponsePayload;
import com.nododiiiii.ponderer.sync.protocol.UploadResponsePayload;
import static com.nododiiiii.ponderer.feedback.protocol.FeedbackPackets.*;

public final class FeedbackResponseDispatch {
    private FeedbackResponseDispatch() {}
    public static void handleResponse(CustomPacketPayload payload) {
        if (payload instanceof RemoteCatalogResponsePayload response) RemoteCatalogResponseClientHandler.handle(response);
        else if (payload instanceof RemoteHistoryResponsePayload response) RemoteHistoryResponseClientHandler.handle(response);
        else if (payload instanceof SyncResponsePayload response) RemotePullClient.handle(response);
        else if (payload instanceof DownloadStructureResultPayload response) DownloadStructureResultClientHandler.handle(response);
        else if (payload instanceof UploadResponsePayload response) UploadResponseClientHandler.handle(response);
        else if (payload instanceof PermissionListResponsePayload response) PermissionListResponseClientHandler.handle(response);
        else if (payload instanceof BlueprintConfigResponsePayload response) BlueprintConfigResponseClientHandler.handle(response);
        else if (payload instanceof ProjectorFeatureConfigResponsePayload response) ProjectorFeatureConfigResponseClientHandler.handle(response);
        else if (payload instanceof RemoteActionResponsePayload response) RemoteActionResponseClientHandler.handle(response);
        else if (payload instanceof FeedbackMessagePayload response) FeedbackMessageHandler.handle(response);
        else throw new IllegalArgumentException("Unsupported feedback response");
    }
}
