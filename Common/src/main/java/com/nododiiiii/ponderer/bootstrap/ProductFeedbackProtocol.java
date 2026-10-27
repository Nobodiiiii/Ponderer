package com.nododiiiii.ponderer.bootstrap;

import com.nododiiiii.ponderer.features.blueprint.protocol.BlueprintConfigRequestPayload;
import com.nododiiiii.ponderer.features.blueprint.protocol.BlueprintConfigResponsePayload;
import com.nododiiiii.ponderer.features.blueprint.protocol.BlueprintConfigUpdatePayload;
import com.nododiiiii.ponderer.features.projector.protocol.ProjectorFeatureConfigRequestPayload;
import com.nododiiiii.ponderer.features.projector.protocol.ProjectorFeatureConfigResponsePayload;
import com.nododiiiii.ponderer.features.projector.protocol.ProjectorFeatureConfigUpdatePayload;
import com.nododiiiii.ponderer.sync.protocol.DownloadStructurePayload;
import com.nododiiiii.ponderer.sync.protocol.DownloadStructureResultPayload;
import com.nododiiiii.ponderer.sync.protocol.PermissionListRequestPayload;
import com.nododiiiii.ponderer.sync.protocol.PermissionListResponsePayload;
import com.nododiiiii.ponderer.sync.protocol.PermissionUpdateRequestPayload;
import com.nododiiiii.ponderer.sync.protocol.RemoteActionResponsePayload;
import com.nododiiiii.ponderer.sync.protocol.RemoteCatalogRequestPayload;
import com.nododiiiii.ponderer.sync.protocol.RemoteCatalogResponsePayload;
import com.nododiiiii.ponderer.sync.protocol.RemoteDeleteRequestPayload;
import com.nododiiiii.ponderer.sync.protocol.RemoteHistoryRequestPayload;
import com.nododiiiii.ponderer.sync.protocol.RemoteHistoryResponsePayload;
import com.nododiiiii.ponderer.sync.protocol.RemotePullRequestPayload;
import com.nododiiiii.ponderer.sync.protocol.RemoteRollbackRequestPayload;
import com.nododiiiii.ponderer.sync.protocol.SyncRequestPayload;
import com.nododiiiii.ponderer.sync.protocol.SyncResponsePayload;
import com.nododiiiii.ponderer.sync.protocol.UploadResponsePayload;
import com.nododiiiii.ponderer.sync.protocol.UploadScenePayload;
import com.nododiiiii.ponderer.feedback.protocol.FeedbackMessagePayload;
import com.nododiiiii.ponderer.feedback.protocol.FeedbackPackets;
import com.nododiiiii.ponderer.feedback.protocol.FeedbackPackets.Entry;
import java.util.List;

/** Product wire order is fixed for compatibility; feature selection never renumbers it. */
public final class ProductFeedbackProtocol {
    private static final List<Entry<?>> REQUESTS = List.of(
        new Entry<>(RemoteCatalogRequestPayload.TYPE, RemoteCatalogRequestPayload.CODEC),
        new Entry<>(RemoteHistoryRequestPayload.TYPE, RemoteHistoryRequestPayload.CODEC),
        new Entry<>(UploadScenePayload.TYPE, UploadScenePayload.CODEC),
        new Entry<>(SyncRequestPayload.TYPE, SyncRequestPayload.CODEC),
        new Entry<>(DownloadStructurePayload.TYPE, DownloadStructurePayload.CODEC),
        new Entry<>(PermissionListRequestPayload.TYPE, PermissionListRequestPayload.CODEC),
        new Entry<>(PermissionUpdateRequestPayload.TYPE, PermissionUpdateRequestPayload.CODEC),
        new Entry<>(BlueprintConfigRequestPayload.TYPE, BlueprintConfigRequestPayload.CODEC),
        new Entry<>(BlueprintConfigUpdatePayload.TYPE, BlueprintConfigUpdatePayload.CODEC),
        new Entry<>(ProjectorFeatureConfigRequestPayload.TYPE, ProjectorFeatureConfigRequestPayload.CODEC),
        new Entry<>(ProjectorFeatureConfigUpdatePayload.TYPE, ProjectorFeatureConfigUpdatePayload.CODEC),
        new Entry<>(RemotePullRequestPayload.TYPE, RemotePullRequestPayload.CODEC),
        new Entry<>(RemoteDeleteRequestPayload.TYPE, RemoteDeleteRequestPayload.CODEC),
        new Entry<>(RemoteRollbackRequestPayload.TYPE, RemoteRollbackRequestPayload.CODEC));
    private static final List<Entry<?>> RESPONSES = List.of(
        new Entry<>(RemoteCatalogResponsePayload.TYPE, RemoteCatalogResponsePayload.CODEC),
        new Entry<>(RemoteHistoryResponsePayload.TYPE, RemoteHistoryResponsePayload.CODEC),
        new Entry<>(SyncResponsePayload.TYPE, SyncResponsePayload.CODEC),
        new Entry<>(DownloadStructureResultPayload.TYPE, DownloadStructureResultPayload.CODEC),
        new Entry<>(UploadResponsePayload.TYPE, UploadResponsePayload.CODEC),
        new Entry<>(PermissionListResponsePayload.TYPE, PermissionListResponsePayload.CODEC),
        new Entry<>(BlueprintConfigResponsePayload.TYPE, BlueprintConfigResponsePayload.CODEC),
        new Entry<>(ProjectorFeatureConfigResponsePayload.TYPE, ProjectorFeatureConfigResponsePayload.CODEC),
        new Entry<>(RemoteActionResponsePayload.TYPE, RemoteActionResponsePayload.CODEC),
        new Entry<>(FeedbackMessagePayload.TYPE, FeedbackMessagePayload.CODEC));

    private ProductFeedbackProtocol() {}
    public static void install() { FeedbackPackets.install(REQUESTS, RESPONSES); }
}
