package com.nododiiiii.ponderer.neoforge;
import com.nododiiiii.ponderer.application.network.BlueprintConfigResponseClientHandler;
import com.nododiiiii.ponderer.application.network.ProjectorFeatureConfigResponseClientHandler;
import com.nododiiiii.ponderer.application.network.UploadResponseClientHandler;
import com.nododiiiii.ponderer.application.network.StructurePreviewResponseClientHandler;
import com.nododiiiii.ponderer.application.network.RemoteHistoryResponseClientHandler;
import com.nododiiiii.ponderer.application.network.RemoteCatalogResponseClientHandler;
import com.nododiiiii.ponderer.application.network.RemoteActionResponseClientHandler;
import com.nododiiiii.ponderer.application.network.PermissionListResponseClientHandler;
import com.nododiiiii.ponderer.application.network.DownloadStructureResultClientHandler;

import com.nododiiiii.ponderer.application.protocol.FeatureAvailabilityPayload;
import com.nododiiiii.ponderer.feedback.protocol.FeedbackMessagePayload;
import com.nododiiiii.ponderer.feedback.protocol.FeedbackRequestPayload;
import com.nododiiiii.ponderer.feedback.protocol.FeedbackResponsePayload;
import com.nododiiiii.ponderer.editor.capture.protocol.CaptureBlockEntityNbtRequestPayload;
import com.nododiiiii.ponderer.editor.capture.protocol.CaptureBlockEntityNbtResponsePayload;
import com.nododiiiii.ponderer.features.blueprint.protocol.BlueprintConfigRequestPayload;
import com.nododiiiii.ponderer.features.blueprint.protocol.BlueprintConfigResponsePayload;
import com.nododiiiii.ponderer.features.blueprint.protocol.BlueprintConfigUpdatePayload;
import com.nododiiiii.ponderer.features.projector.protocol.ProjectorConfigUpdatePayload;
import com.nododiiiii.ponderer.features.projector.protocol.ProjectorFeatureConfigRequestPayload;
import com.nododiiiii.ponderer.features.projector.protocol.ProjectorFeatureConfigResponsePayload;
import com.nododiiiii.ponderer.features.projector.protocol.ProjectorFeatureConfigUpdatePayload;
import com.nododiiiii.ponderer.features.projector.protocol.ProjectorPlaybackStartPayload;
import com.nododiiiii.ponderer.sync.protocol.AutoSyncPreferencePayload;
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
import com.nododiiiii.ponderer.sync.protocol.StructurePreviewRequestPayload;
import com.nododiiiii.ponderer.sync.protocol.StructurePreviewResponsePayload;
import com.nododiiiii.ponderer.sync.protocol.SyncRequestPayload;
import com.nododiiiii.ponderer.sync.protocol.SyncResponsePayload;
import com.nododiiiii.ponderer.sync.protocol.UploadResponsePayload;
import com.nododiiiii.ponderer.sync.protocol.UploadScenePayload;
import com.nododiiiii.ponderer.sync.network.UploadSceneHandler;
import com.nododiiiii.ponderer.sync.client.RemotePullClient;
import com.nododiiiii.ponderer.sync.network.SyncRequestHandler;
import com.nododiiiii.ponderer.sync.network.StructurePreviewRequestHandler;
import com.nododiiiii.ponderer.sync.network.RemoteRollbackRequestHandler;
import com.nododiiiii.ponderer.sync.network.RemotePullRequestHandler;
import com.nododiiiii.ponderer.sync.network.RemoteHistoryRequestHandler;
import com.nododiiiii.ponderer.sync.network.RemoteDeleteRequestHandler;
import com.nododiiiii.ponderer.sync.network.RemoteCatalogRequestHandler;
import com.nododiiiii.ponderer.features.projector.network.ProjectorPlaybackStartHandler;
import com.nododiiiii.ponderer.features.projector.network.ProjectorFeatureConfigUpdateHandler;
import com.nododiiiii.ponderer.features.projector.network.ProjectorFeatureConfigRequestHandler;
import com.nododiiiii.ponderer.features.projector.network.ProjectorConfigUpdateHandler;
import com.nododiiiii.ponderer.sync.network.PermissionUpdateRequestHandler;
import com.nododiiiii.ponderer.sync.network.PermissionListRequestHandler;
import com.nododiiiii.ponderer.application.network.FeedbackRequestHandler;
import com.nododiiiii.ponderer.application.network.FeedbackMessageHandler;
import com.nododiiiii.ponderer.application.network.FeatureAvailabilityHandler;
import com.nododiiiii.ponderer.sync.network.DownloadStructureHandler;
import com.nododiiiii.ponderer.editor.capture.network.CaptureBlockEntityNbtResponseHandler;
import com.nododiiiii.ponderer.editor.capture.network.CaptureBlockEntityNbtRequestHandler;
import com.nododiiiii.ponderer.features.blueprint.network.BlueprintConfigUpdateHandler;
import com.nododiiiii.ponderer.features.blueprint.network.BlueprintConfigRequestHandler;
import com.nododiiiii.ponderer.sync.network.AutoSyncPreferenceHandler;
import com.nododiiiii.ponderer.feedback.ServerFeedback;
import com.nododiiiii.ponderer.feedback.ClientFeedback;
import com.nododiiiii.ponderer.platform.services.NetworkHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NeoForge implementation of NetworkHelper using PayloadRegistrar.
 */
public class NeoForgeNetworkHelper implements NetworkHelper {

    public static final String VERSION = "5";

    /**
     * Called from PondererNeoForge when RegisterPayloadHandlersEvent fires.
     */
    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(FeedbackRequestPayload.TYPE, FeedbackRequestPayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> FeedbackRequestHandler.handle(payload, (ServerPlayer) ctx.player())));
        registrar.playToClient(FeedbackResponsePayload.TYPE, FeedbackResponsePayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> ClientFeedback.receive(payload)));
        registrar.playToClient(FeedbackMessagePayload.TYPE, FeedbackMessagePayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> FeedbackMessageHandler.handle(payload)));
        event.registrar(VERSION).optional().playToServer(AutoSyncPreferencePayload.TYPE, AutoSyncPreferencePayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> AutoSyncPreferenceHandler.handle(payload, (ServerPlayer) ctx.player())));

        registrar.playToServer(UploadScenePayload.TYPE, UploadScenePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> UploadSceneHandler.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(SyncRequestPayload.TYPE, SyncRequestPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> SyncRequestHandler.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(DownloadStructurePayload.TYPE, DownloadStructurePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> DownloadStructureHandler.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(StructurePreviewRequestPayload.TYPE, StructurePreviewRequestPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> StructurePreviewRequestHandler.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(CaptureBlockEntityNbtRequestPayload.TYPE, CaptureBlockEntityNbtRequestPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> CaptureBlockEntityNbtRequestHandler.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(PermissionListRequestPayload.TYPE, PermissionListRequestPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> PermissionListRequestHandler.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(PermissionUpdateRequestPayload.TYPE, PermissionUpdateRequestPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> PermissionUpdateRequestHandler.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(BlueprintConfigRequestPayload.TYPE, BlueprintConfigRequestPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> BlueprintConfigRequestHandler.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(BlueprintConfigUpdatePayload.TYPE, BlueprintConfigUpdatePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> BlueprintConfigUpdateHandler.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(ProjectorConfigUpdatePayload.TYPE, ProjectorConfigUpdatePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> ProjectorConfigUpdateHandler.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(ProjectorFeatureConfigRequestPayload.TYPE, ProjectorFeatureConfigRequestPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> ProjectorFeatureConfigRequestHandler.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(ProjectorFeatureConfigUpdatePayload.TYPE, ProjectorFeatureConfigUpdatePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> ProjectorFeatureConfigUpdateHandler.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(RemoteCatalogRequestPayload.TYPE, RemoteCatalogRequestPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> RemoteCatalogRequestHandler.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(RemotePullRequestPayload.TYPE, RemotePullRequestPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> RemotePullRequestHandler.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(RemoteDeleteRequestPayload.TYPE, RemoteDeleteRequestPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> RemoteDeleteRequestHandler.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(RemoteHistoryRequestPayload.TYPE, RemoteHistoryRequestPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> RemoteHistoryRequestHandler.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(RemoteRollbackRequestPayload.TYPE, RemoteRollbackRequestPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> RemoteRollbackRequestHandler.handle(payload, (ServerPlayer) ctx.player()));
        });

        registrar.playToClient(SyncResponsePayload.TYPE, SyncResponsePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> RemotePullClient.handle(payload));
        });
        registrar.playToClient(DownloadStructureResultPayload.TYPE, DownloadStructureResultPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> DownloadStructureResultClientHandler.handle(payload));
        });
        registrar.playToClient(StructurePreviewResponsePayload.TYPE, StructurePreviewResponsePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> StructurePreviewResponseClientHandler.handle(payload));
        });
        registrar.playToClient(UploadResponsePayload.TYPE, UploadResponsePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> UploadResponseClientHandler.handle(payload));
        });
        registrar.playToClient(CaptureBlockEntityNbtResponsePayload.TYPE, CaptureBlockEntityNbtResponsePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> CaptureBlockEntityNbtResponseHandler.handle(payload));
        });
        registrar.playToClient(PermissionListResponsePayload.TYPE, PermissionListResponsePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> PermissionListResponseClientHandler.handle(payload));
        });
        registrar.playToClient(BlueprintConfigResponsePayload.TYPE, BlueprintConfigResponsePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> BlueprintConfigResponseClientHandler.handle(payload));
        });
        registrar.playToClient(ProjectorFeatureConfigResponsePayload.TYPE, ProjectorFeatureConfigResponsePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> ProjectorFeatureConfigResponseClientHandler.handle(payload));
        });
        registrar.playToClient(ProjectorPlaybackStartPayload.TYPE, ProjectorPlaybackStartPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> ProjectorPlaybackStartHandler.handle(payload));
        });
        registrar.playToClient(FeatureAvailabilityPayload.TYPE, FeatureAvailabilityPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> FeatureAvailabilityHandler.handle(payload));
        });
        registrar.playToClient(RemoteCatalogResponsePayload.TYPE, RemoteCatalogResponsePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> RemoteCatalogResponseClientHandler.handle(payload));
        });
        registrar.playToClient(RemoteActionResponsePayload.TYPE, RemoteActionResponsePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> RemoteActionResponseClientHandler.handle(payload));
        });
        registrar.playToClient(RemoteHistoryResponsePayload.TYPE, RemoteHistoryResponsePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> RemoteHistoryResponseClientHandler.handle(payload));
        });
    }

    @Override
    public void registerPackets() {
        // No-op for NeoForge; done via registerPayloads() called from the entry point.
    }

    @Override
    public void sendToServer(CustomPacketPayload packet) {
        PacketDistributor.sendToServer(ClientFeedback.request(packet));
    }

    @Override
    public boolean canSendToServer(CustomPacketPayload.Type<?> type) {
        var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(type);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload packet) {
        PacketDistributor.sendToPlayer(player, ServerFeedback.response(player, packet));
    }
}
