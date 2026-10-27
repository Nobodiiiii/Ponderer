package com.nododiiiii.ponderer.application.network;

import com.nododiiiii.ponderer.sync.network.UploadSceneHandler;
import com.nododiiiii.ponderer.sync.network.SyncRequestHandler;
import com.nododiiiii.ponderer.sync.network.RemoteRollbackRequestHandler;
import com.nododiiiii.ponderer.sync.network.RemotePullRequestHandler;
import com.nododiiiii.ponderer.sync.network.RemoteHistoryRequestHandler;
import com.nododiiiii.ponderer.sync.network.RemoteDeleteRequestHandler;
import com.nododiiiii.ponderer.sync.network.RemoteCatalogRequestHandler;
import com.nododiiiii.ponderer.features.projector.network.ProjectorFeatureConfigUpdateHandler;
import com.nododiiiii.ponderer.features.projector.network.ProjectorFeatureConfigRequestHandler;
import com.nododiiiii.ponderer.sync.network.PermissionUpdateRequestHandler;
import com.nododiiiii.ponderer.sync.network.PermissionListRequestHandler;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import com.nododiiiii.ponderer.features.blueprint.network.BlueprintConfigRequestHandler;
import com.nododiiiii.ponderer.features.blueprint.protocol.BlueprintConfigRequestPayload;
import com.nododiiiii.ponderer.features.blueprint.network.BlueprintConfigUpdateHandler;
import com.nododiiiii.ponderer.features.blueprint.protocol.BlueprintConfigUpdatePayload;
import com.nododiiiii.ponderer.sync.network.DownloadStructureHandler;
import com.nododiiiii.ponderer.sync.protocol.DownloadStructurePayload;
import com.nododiiiii.ponderer.sync.protocol.PermissionListRequestPayload;
import com.nododiiiii.ponderer.sync.protocol.PermissionUpdateRequestPayload;
import com.nododiiiii.ponderer.features.projector.protocol.ProjectorFeatureConfigRequestPayload;
import com.nododiiiii.ponderer.features.projector.protocol.ProjectorFeatureConfigUpdatePayload;
import com.nododiiiii.ponderer.sync.protocol.RemoteCatalogRequestPayload;
import com.nododiiiii.ponderer.sync.protocol.RemoteDeleteRequestPayload;
import com.nododiiiii.ponderer.sync.protocol.RemoteHistoryRequestPayload;
import com.nododiiiii.ponderer.sync.protocol.RemotePullRequestPayload;
import com.nododiiiii.ponderer.sync.protocol.RemoteRollbackRequestPayload;
import com.nododiiiii.ponderer.sync.protocol.SyncRequestPayload;
import com.nododiiiii.ponderer.sync.protocol.UploadScenePayload;
import static com.nododiiiii.ponderer.feedback.protocol.FeedbackPackets.*;

public final class FeedbackRequestDispatch {
    private FeedbackRequestDispatch() {}
    public static void handleRequest(CustomPacketPayload payload, ServerPlayer player) {
        if (payload instanceof RemoteCatalogRequestPayload request) RemoteCatalogRequestHandler.handle(request, player);
        else if (payload instanceof RemoteHistoryRequestPayload request) RemoteHistoryRequestHandler.handle(request, player);
        else if (payload instanceof UploadScenePayload request) UploadSceneHandler.handle(request, player);
        else if (payload instanceof SyncRequestPayload request) SyncRequestHandler.handle(request, player);
        else if (payload instanceof DownloadStructurePayload request) DownloadStructureHandler.handle(request, player);
        else if (payload instanceof PermissionListRequestPayload request) PermissionListRequestHandler.handle(request, player);
        else if (payload instanceof PermissionUpdateRequestPayload request) PermissionUpdateRequestHandler.handle(request, player);
        else if (payload instanceof BlueprintConfigRequestPayload request) BlueprintConfigRequestHandler.handle(request, player);
        else if (payload instanceof BlueprintConfigUpdatePayload request) BlueprintConfigUpdateHandler.handle(request, player);
        else if (payload instanceof ProjectorFeatureConfigRequestPayload request) ProjectorFeatureConfigRequestHandler.handle(request, player);
        else if (payload instanceof ProjectorFeatureConfigUpdatePayload request) ProjectorFeatureConfigUpdateHandler.handle(request, player);
        else if (payload instanceof RemotePullRequestPayload request) RemotePullRequestHandler.handle(request, player);
        else if (payload instanceof RemoteDeleteRequestPayload request) RemoteDeleteRequestHandler.handle(request, player);
        else if (payload instanceof RemoteRollbackRequestPayload request) RemoteRollbackRequestHandler.handle(request, player);
        else throw new IllegalArgumentException("Unsupported feedback request");
    }
}
