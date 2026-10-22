package com.nododiiiii.ponderer.network;

import net.minecraft.network.FriendlyByteBuf;
import com.nododiiiii.ponderer.network.compat.StreamCodec;
import com.nododiiiii.ponderer.network.compat.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;

/** Direction-specific allowlists prevent nesting envelopes or dispatching a response as a server request. */
public final class FeedbackPackets {
    private record Entry<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type, StreamCodec<FriendlyByteBuf, T> codec) {
        void encode(FriendlyByteBuf buf, CustomPacketPayload payload) {
            @SuppressWarnings("unchecked") T typed = (T) payload;
            codec.encode(buf, typed);
        }
    }
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

    private FeedbackPackets() {}

    public static boolean isResponse(CustomPacketPayload payload) {
        return RESPONSES.stream().anyMatch(entry -> entry.type().equals(payload.type()));
    }

    public static boolean isRequest(CustomPacketPayload payload) {
        return REQUESTS.stream().anyMatch(entry -> entry.type().equals(payload.type()));
    }

    private static void encode(FriendlyByteBuf buf, CustomPacketPayload payload, List<Entry<?>> entries) {
        for (int i = 0; i < entries.size(); i++) {
            Entry<?> entry = entries.get(i);
            if (!entry.type().equals(payload.type())) continue;
            buf.writeVarInt(i);
            entry.encode(buf, payload);
            return;
        }
        throw new IllegalArgumentException("Unsupported feedback payload: " + payload.type().id());
    }

    private static CustomPacketPayload decode(FriendlyByteBuf buf, List<Entry<?>> entries) {
        int index = buf.readVarInt();
        if (index < 0 || index >= entries.size()) throw new IllegalArgumentException("Invalid feedback payload index: " + index);
        return entries.get(index).codec().decode(buf);
    }

    public static void encodeRequest(FriendlyByteBuf buf, CustomPacketPayload payload) { encode(buf, payload, REQUESTS); }
    public static CustomPacketPayload decodeRequest(FriendlyByteBuf buf) { return decode(buf, REQUESTS); }
    public static void encodeResponse(FriendlyByteBuf buf, CustomPacketPayload payload) { encode(buf, payload, RESPONSES); }
    public static CustomPacketPayload decodeResponse(FriendlyByteBuf buf) { return decode(buf, RESPONSES); }

    public static void handleRequest(CustomPacketPayload payload, ServerPlayer player) {
        if (payload instanceof RemoteCatalogRequestPayload request) RemoteCatalogRequestPayload.handle(request, player);
        else if (payload instanceof RemoteHistoryRequestPayload request) RemoteHistoryRequestPayload.handle(request, player);
        else if (payload instanceof UploadScenePayload request) UploadScenePayload.handle(request, player);
        else if (payload instanceof SyncRequestPayload request) SyncRequestPayload.handle(request, player);
        else if (payload instanceof DownloadStructurePayload request) DownloadStructurePayload.handle(request, player);
        else if (payload instanceof PermissionListRequestPayload request) PermissionListRequestPayload.handle(request, player);
        else if (payload instanceof PermissionUpdateRequestPayload request) PermissionUpdateRequestPayload.handle(request, player);
        else if (payload instanceof BlueprintConfigRequestPayload request) BlueprintConfigRequestPayload.handle(request, player);
        else if (payload instanceof BlueprintConfigUpdatePayload request) BlueprintConfigUpdatePayload.handle(request, player);
        else if (payload instanceof ProjectorFeatureConfigRequestPayload request) ProjectorFeatureConfigRequestPayload.handle(request, player);
        else if (payload instanceof ProjectorFeatureConfigUpdatePayload request) ProjectorFeatureConfigUpdatePayload.handle(request, player);
        else if (payload instanceof RemotePullRequestPayload request) RemotePullRequestPayload.handle(request, player);
        else if (payload instanceof RemoteDeleteRequestPayload request) RemoteDeleteRequestPayload.handle(request, player);
        else if (payload instanceof RemoteRollbackRequestPayload request) RemoteRollbackRequestPayload.handle(request, player);
        else throw new IllegalArgumentException("Unsupported feedback request");
    }

    public static void handleResponse(CustomPacketPayload payload) {
        if (payload instanceof RemoteCatalogResponsePayload response) RemoteCatalogResponsePayload.handle(response);
        else if (payload instanceof RemoteHistoryResponsePayload response) RemoteHistoryResponsePayload.handle(response);
        else if (payload instanceof SyncResponsePayload response) SyncResponsePayload.handle(response);
        else if (payload instanceof DownloadStructureResultPayload response) DownloadStructureResultPayload.handle(response);
        else if (payload instanceof UploadResponsePayload response) UploadResponsePayload.handle(response);
        else if (payload instanceof PermissionListResponsePayload response) PermissionListResponsePayload.handle(response);
        else if (payload instanceof BlueprintConfigResponsePayload response) BlueprintConfigResponsePayload.handle(response);
        else if (payload instanceof ProjectorFeatureConfigResponsePayload response) ProjectorFeatureConfigResponsePayload.handle(response);
        else if (payload instanceof RemoteActionResponsePayload response) RemoteActionResponsePayload.handle(response);
        else if (payload instanceof FeedbackMessagePayload response) FeedbackMessagePayload.handle(response);
        else throw new IllegalArgumentException("Unsupported feedback response");
    }
}
