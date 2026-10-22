package com.nododiiiii.ponderer.fabric;

import com.nododiiiii.ponderer.feedback.ClientFeedback;
import com.nododiiiii.ponderer.feedback.ServerFeedback;
import com.nododiiiii.ponderer.network.*;
import com.nododiiiii.ponderer.network.compat.CustomPacketPayload;
import com.nododiiiii.ponderer.network.compat.StreamCodec;
import com.nododiiiii.ponderer.platform.services.NetworkHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class FabricNetworkHelper implements NetworkHelper {
    @Override public void registerPackets() {
        registerServer(FeedbackRequestPayload.TYPE, FeedbackRequestPayload.CODEC, FeedbackRequestPayload::handle);
        registerServer(AutoSyncPreferencePayload.TYPE, AutoSyncPreferencePayload.CODEC, AutoSyncPreferencePayload::handle);
        registerServer(UploadScenePayload.TYPE, UploadScenePayload.CODEC, UploadScenePayload::handle);
        registerServer(SyncRequestPayload.TYPE, SyncRequestPayload.CODEC, SyncRequestPayload::handle);
        registerServer(DownloadStructurePayload.TYPE, DownloadStructurePayload.CODEC, DownloadStructurePayload::handle);
        registerServer(StructurePreviewRequestPayload.TYPE, StructurePreviewRequestPayload.CODEC, StructurePreviewRequestPayload::handle);
        registerServer(CaptureBlockEntityNbtRequestPayload.TYPE, CaptureBlockEntityNbtRequestPayload.CODEC, CaptureBlockEntityNbtRequestPayload::handle);
        registerServer(PermissionListRequestPayload.TYPE, PermissionListRequestPayload.CODEC, PermissionListRequestPayload::handle);
        registerServer(PermissionUpdateRequestPayload.TYPE, PermissionUpdateRequestPayload.CODEC, PermissionUpdateRequestPayload::handle);
        registerServer(BlueprintConfigRequestPayload.TYPE, BlueprintConfigRequestPayload.CODEC, BlueprintConfigRequestPayload::handle);
        registerServer(BlueprintConfigUpdatePayload.TYPE, BlueprintConfigUpdatePayload.CODEC, BlueprintConfigUpdatePayload::handle);
        registerServer(ProjectorConfigUpdatePayload.TYPE, ProjectorConfigUpdatePayload.CODEC, ProjectorConfigUpdatePayload::handle);
        registerServer(ProjectorFeatureConfigRequestPayload.TYPE, ProjectorFeatureConfigRequestPayload.CODEC, ProjectorFeatureConfigRequestPayload::handle);
        registerServer(ProjectorFeatureConfigUpdatePayload.TYPE, ProjectorFeatureConfigUpdatePayload.CODEC, ProjectorFeatureConfigUpdatePayload::handle);
        registerServer(RemoteCatalogRequestPayload.TYPE, RemoteCatalogRequestPayload.CODEC, RemoteCatalogRequestPayload::handle);
        registerServer(RemotePullRequestPayload.TYPE, RemotePullRequestPayload.CODEC, RemotePullRequestPayload::handle);
        registerServer(RemoteDeleteRequestPayload.TYPE, RemoteDeleteRequestPayload.CODEC, RemoteDeleteRequestPayload::handle);
        registerServer(RemoteHistoryRequestPayload.TYPE, RemoteHistoryRequestPayload.CODEC, RemoteHistoryRequestPayload::handle);
        registerServer(RemoteRollbackRequestPayload.TYPE, RemoteRollbackRequestPayload.CODEC, RemoteRollbackRequestPayload::handle);
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) registerClientPackets();
    }
    private void registerClientPackets() {
        registerClient(FeedbackResponsePayload.TYPE, FeedbackResponsePayload.CODEC, ClientFeedback::receive);
        registerClient(FeedbackMessagePayload.TYPE, FeedbackMessagePayload.CODEC, FeedbackMessagePayload::handle);
        registerClient(SyncResponsePayload.TYPE, SyncResponsePayload.CODEC, SyncResponsePayload::handle);
        registerClient(DownloadStructureResultPayload.TYPE, DownloadStructureResultPayload.CODEC, DownloadStructureResultPayload::handle);
        registerClient(StructurePreviewResponsePayload.TYPE, StructurePreviewResponsePayload.CODEC, StructurePreviewResponsePayload::handle);
        registerClient(UploadResponsePayload.TYPE, UploadResponsePayload.CODEC, UploadResponsePayload::handle);
        registerClient(CaptureBlockEntityNbtResponsePayload.TYPE, CaptureBlockEntityNbtResponsePayload.CODEC, CaptureBlockEntityNbtResponsePayload::handle);
        registerClient(PermissionListResponsePayload.TYPE, PermissionListResponsePayload.CODEC, PermissionListResponsePayload::handle);
        registerClient(BlueprintConfigResponsePayload.TYPE, BlueprintConfigResponsePayload.CODEC, BlueprintConfigResponsePayload::handle);
        registerClient(ProjectorFeatureConfigResponsePayload.TYPE, ProjectorFeatureConfigResponsePayload.CODEC, ProjectorFeatureConfigResponsePayload::handle);
        registerClient(ProjectorPlaybackStartPayload.TYPE, ProjectorPlaybackStartPayload.CODEC, ProjectorPlaybackStartPayload::handle);
        registerClient(FeatureAvailabilityPayload.TYPE, FeatureAvailabilityPayload.CODEC, FeatureAvailabilityPayload::handle);
        registerClient(RemoteCatalogResponsePayload.TYPE, RemoteCatalogResponsePayload.CODEC, RemoteCatalogResponsePayload::handle);
        registerClient(RemoteActionResponsePayload.TYPE, RemoteActionResponsePayload.CODEC, RemoteActionResponsePayload::handle);
        registerClient(RemoteHistoryResponsePayload.TYPE, RemoteHistoryResponsePayload.CODEC, RemoteHistoryResponsePayload::handle);
    }
    private <T extends CustomPacketPayload> void registerServer(CustomPacketPayload.Type<T> type, StreamCodec<FriendlyByteBuf, T> codec,
                                                               BiConsumer<T, ServerPlayer> handler) {
        ServerPlayNetworking.registerGlobalReceiver(type.id(), (server, player, connection, buf, sender) -> {
            T payload = codec.decode(buf);
            server.execute(() -> handler.accept(payload, player));
        });
    }
    private <T extends CustomPacketPayload> void registerClient(CustomPacketPayload.Type<T> type, StreamCodec<FriendlyByteBuf, T> codec,
                                                               Consumer<T> handler) {
        ClientPlayNetworking.registerGlobalReceiver(type.id(), (client, connection, buf, sender) -> {
            T payload = codec.decode(buf);
            client.execute(() -> handler.accept(payload));
        });
    }
    @Override public void sendToServer(CustomPacketPayload packet) { sendClient(ClientFeedback.request(packet)); }
    private <T extends CustomPacketPayload> void sendClient(T packet) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        codec(packet).encode(buf, packet);
        ClientPlayNetworking.send(packet.type().id(), buf);
    }
    @Override public void sendToPlayer(ServerPlayer player, CustomPacketPayload packet) { sendPlayer(player, ServerFeedback.response(player, packet)); }
    private <T extends CustomPacketPayload> void sendPlayer(ServerPlayer player, T packet) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        codec(packet).encode(buf, packet);
        ServerPlayNetworking.send(player, packet.type().id(), buf);
    }
    @SuppressWarnings("unchecked")
    private <T extends CustomPacketPayload> StreamCodec<FriendlyByteBuf, T> codec(T packet) {
        if (packet instanceof FeedbackRequestPayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) FeedbackRequestPayload.CODEC;
        if (packet instanceof FeedbackResponsePayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) FeedbackResponsePayload.CODEC;
        if (packet instanceof FeedbackMessagePayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) FeedbackMessagePayload.CODEC;
        if (packet instanceof AutoSyncPreferencePayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) AutoSyncPreferencePayload.CODEC;
        if (packet instanceof UploadScenePayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) UploadScenePayload.CODEC;
        if (packet instanceof SyncRequestPayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) SyncRequestPayload.CODEC;
        if (packet instanceof DownloadStructurePayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) DownloadStructurePayload.CODEC;
        if (packet instanceof StructurePreviewRequestPayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) StructurePreviewRequestPayload.CODEC;
        if (packet instanceof CaptureBlockEntityNbtRequestPayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) CaptureBlockEntityNbtRequestPayload.CODEC;
        if (packet instanceof PermissionListRequestPayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) PermissionListRequestPayload.CODEC;
        if (packet instanceof PermissionUpdateRequestPayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) PermissionUpdateRequestPayload.CODEC;
        if (packet instanceof BlueprintConfigRequestPayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) BlueprintConfigRequestPayload.CODEC;
        if (packet instanceof BlueprintConfigUpdatePayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) BlueprintConfigUpdatePayload.CODEC;
        if (packet instanceof ProjectorConfigUpdatePayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) ProjectorConfigUpdatePayload.CODEC;
        if (packet instanceof ProjectorFeatureConfigRequestPayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) ProjectorFeatureConfigRequestPayload.CODEC;
        if (packet instanceof ProjectorFeatureConfigUpdatePayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) ProjectorFeatureConfigUpdatePayload.CODEC;
        if (packet instanceof RemoteCatalogRequestPayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) RemoteCatalogRequestPayload.CODEC;
        if (packet instanceof RemotePullRequestPayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) RemotePullRequestPayload.CODEC;
        if (packet instanceof RemoteDeleteRequestPayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) RemoteDeleteRequestPayload.CODEC;
        if (packet instanceof RemoteHistoryRequestPayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) RemoteHistoryRequestPayload.CODEC;
        if (packet instanceof RemoteRollbackRequestPayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) RemoteRollbackRequestPayload.CODEC;
        if (packet instanceof SyncResponsePayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) SyncResponsePayload.CODEC;
        if (packet instanceof DownloadStructureResultPayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) DownloadStructureResultPayload.CODEC;
        if (packet instanceof StructurePreviewResponsePayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) StructurePreviewResponsePayload.CODEC;
        if (packet instanceof UploadResponsePayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) UploadResponsePayload.CODEC;
        if (packet instanceof CaptureBlockEntityNbtResponsePayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) CaptureBlockEntityNbtResponsePayload.CODEC;
        if (packet instanceof PermissionListResponsePayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) PermissionListResponsePayload.CODEC;
        if (packet instanceof BlueprintConfigResponsePayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) BlueprintConfigResponsePayload.CODEC;
        if (packet instanceof ProjectorFeatureConfigResponsePayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) ProjectorFeatureConfigResponsePayload.CODEC;
        if (packet instanceof ProjectorPlaybackStartPayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) ProjectorPlaybackStartPayload.CODEC;
        if (packet instanceof FeatureAvailabilityPayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) FeatureAvailabilityPayload.CODEC;
        if (packet instanceof RemoteCatalogResponsePayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) RemoteCatalogResponsePayload.CODEC;
        if (packet instanceof RemoteActionResponsePayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) RemoteActionResponsePayload.CODEC;
        if (packet instanceof RemoteHistoryResponsePayload) return (StreamCodec<FriendlyByteBuf, T>) (StreamCodec<?, ?>) RemoteHistoryResponsePayload.CODEC;
        throw new IllegalArgumentException("Unknown Ponderer payload: " + packet.type().id());
    }
    @Override public boolean canSendToServer(CustomPacketPayload.Type<?> type) { return ClientPlayNetworking.canSend(type.id()); }
}
