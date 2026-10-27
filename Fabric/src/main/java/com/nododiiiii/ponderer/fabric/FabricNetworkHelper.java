package com.nododiiiii.ponderer.fabric;
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
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric implementation of NetworkHelper using Fabric Networking API v1 (1.21.1+).
 */
public class FabricNetworkHelper implements NetworkHelper {

    @Override
    public void registerPackets() {
        PayloadTypeRegistry.playC2S().register(FeedbackRequestPayload.TYPE, FeedbackRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FeedbackResponsePayload.TYPE, FeedbackResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FeedbackMessagePayload.TYPE, FeedbackMessagePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UploadScenePayload.TYPE, UploadScenePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SyncRequestPayload.TYPE, SyncRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AutoSyncPreferencePayload.TYPE, AutoSyncPreferencePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DownloadStructurePayload.TYPE, DownloadStructurePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(StructurePreviewRequestPayload.TYPE, StructurePreviewRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CaptureBlockEntityNbtRequestPayload.TYPE, CaptureBlockEntityNbtRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PermissionListRequestPayload.TYPE, PermissionListRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PermissionUpdateRequestPayload.TYPE, PermissionUpdateRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BlueprintConfigRequestPayload.TYPE, BlueprintConfigRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BlueprintConfigUpdatePayload.TYPE, BlueprintConfigUpdatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ProjectorConfigUpdatePayload.TYPE, ProjectorConfigUpdatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ProjectorFeatureConfigRequestPayload.TYPE, ProjectorFeatureConfigRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ProjectorFeatureConfigUpdatePayload.TYPE, ProjectorFeatureConfigUpdatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RemoteCatalogRequestPayload.TYPE, RemoteCatalogRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RemotePullRequestPayload.TYPE, RemotePullRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RemoteDeleteRequestPayload.TYPE, RemoteDeleteRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RemoteHistoryRequestPayload.TYPE, RemoteHistoryRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RemoteRollbackRequestPayload.TYPE, RemoteRollbackRequestPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(SyncResponsePayload.TYPE, SyncResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DownloadStructureResultPayload.TYPE, DownloadStructureResultPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StructurePreviewResponsePayload.TYPE, StructurePreviewResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(UploadResponsePayload.TYPE, UploadResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CaptureBlockEntityNbtResponsePayload.TYPE, CaptureBlockEntityNbtResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PermissionListResponsePayload.TYPE, PermissionListResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BlueprintConfigResponsePayload.TYPE, BlueprintConfigResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ProjectorFeatureConfigResponsePayload.TYPE, ProjectorFeatureConfigResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ProjectorPlaybackStartPayload.TYPE, ProjectorPlaybackStartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FeatureAvailabilityPayload.TYPE, FeatureAvailabilityPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RemoteCatalogResponsePayload.TYPE, RemoteCatalogResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RemoteActionResponsePayload.TYPE, RemoteActionResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RemoteHistoryResponsePayload.TYPE, RemoteHistoryResponsePayload.CODEC);

        registerServerHandlers();
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            registerClientHandlers();
        }
    }

    private void registerServerHandlers() {
        ServerPlayNetworking.registerGlobalReceiver(FeedbackRequestPayload.TYPE, (payload, context) ->
            context.server().execute(() -> FeedbackRequestHandler.handle(payload, context.player())));
        ServerPlayNetworking.registerGlobalReceiver(StructurePreviewRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.server.execute(() -> StructurePreviewRequestHandler.handle(payload, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(UploadScenePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> UploadSceneHandler.handle(payload, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(SyncRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> SyncRequestHandler.handle(payload, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(AutoSyncPreferencePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.server.execute(() -> AutoSyncPreferenceHandler.handle(payload, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(DownloadStructurePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> DownloadStructureHandler.handle(payload, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(CaptureBlockEntityNbtRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> CaptureBlockEntityNbtRequestHandler.handle(payload, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(PermissionListRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> PermissionListRequestHandler.handle(payload, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(PermissionUpdateRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> PermissionUpdateRequestHandler.handle(payload, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(BlueprintConfigRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> BlueprintConfigRequestHandler.handle(payload, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(BlueprintConfigUpdatePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> BlueprintConfigUpdateHandler.handle(payload, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(ProjectorConfigUpdatePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> ProjectorConfigUpdateHandler.handle(payload, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(ProjectorFeatureConfigRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> ProjectorFeatureConfigRequestHandler.handle(payload, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(ProjectorFeatureConfigUpdatePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> ProjectorFeatureConfigUpdateHandler.handle(payload, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(RemoteCatalogRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> RemoteCatalogRequestHandler.handle(payload, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(RemotePullRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> RemotePullRequestHandler.handle(payload, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(RemoteDeleteRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> RemoteDeleteRequestHandler.handle(payload, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(RemoteHistoryRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> RemoteHistoryRequestHandler.handle(payload, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(RemoteRollbackRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> RemoteRollbackRequestHandler.handle(payload, player));
        });
    }

    @Environment(EnvType.CLIENT)
    private void registerClientHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(FeedbackResponsePayload.TYPE, (payload, context) ->
            context.client().execute(() -> ClientFeedback.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(FeedbackMessagePayload.TYPE, (payload, context) ->
            context.client().execute(() -> FeedbackMessageHandler.handle(payload)));
        ClientPlayNetworking.registerGlobalReceiver(StructurePreviewResponsePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> StructurePreviewResponseClientHandler.handle(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(SyncResponsePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> RemotePullClient.handle(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(DownloadStructureResultPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> DownloadStructureResultClientHandler.handle(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(UploadResponsePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> UploadResponseClientHandler.handle(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(CaptureBlockEntityNbtResponsePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> CaptureBlockEntityNbtResponseHandler.handle(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(PermissionListResponsePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> PermissionListResponseClientHandler.handle(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(BlueprintConfigResponsePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> BlueprintConfigResponseClientHandler.handle(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(ProjectorFeatureConfigResponsePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> ProjectorFeatureConfigResponseClientHandler.handle(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(ProjectorPlaybackStartPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> ProjectorPlaybackStartHandler.handle(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(FeatureAvailabilityPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> FeatureAvailabilityHandler.handle(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(RemoteCatalogResponsePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> RemoteCatalogResponseClientHandler.handle(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(RemoteActionResponsePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> RemoteActionResponseClientHandler.handle(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(RemoteHistoryResponsePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> RemoteHistoryResponseClientHandler.handle(payload));
        });
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void sendToServer(CustomPacketPayload packet) {
        ClientPlayNetworking.send(ClientFeedback.request(packet));
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean canSendToServer(CustomPacketPayload.Type<?> type) {
        return ClientPlayNetworking.canSend(type);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload packet) {
        ServerPlayNetworking.send(player, ServerFeedback.response(player, packet));
    }
}
