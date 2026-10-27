package com.nododiiiii.ponderer.feedback.protocol;

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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.List;

/** Direction-specific allowlists prevent nesting envelopes or dispatching a response as a server request. */
public final class FeedbackPackets {
    private record Entry<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type, StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        void encode(RegistryFriendlyByteBuf buf, CustomPacketPayload payload) {
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

    private static void encode(RegistryFriendlyByteBuf buf, CustomPacketPayload payload, List<Entry<?>> entries) {
        for (int i = 0; i < entries.size(); i++) {
            Entry<?> entry = entries.get(i);
            if (!entry.type().equals(payload.type())) continue;
            buf.writeVarInt(i);
            entry.encode(buf, payload);
            return;
        }
        throw new IllegalArgumentException("Unsupported feedback payload: " + payload.type().id());
    }

    private static CustomPacketPayload decode(RegistryFriendlyByteBuf buf, List<Entry<?>> entries) {
        int index = buf.readVarInt();
        if (index < 0 || index >= entries.size()) throw new IllegalArgumentException("Invalid feedback payload index: " + index);
        return entries.get(index).codec().decode(buf);
    }

    public static void encodeRequest(RegistryFriendlyByteBuf buf, CustomPacketPayload payload) { encode(buf, payload, REQUESTS); }
    public static CustomPacketPayload decodeRequest(RegistryFriendlyByteBuf buf) { return decode(buf, REQUESTS); }
    public static void encodeResponse(RegistryFriendlyByteBuf buf, CustomPacketPayload payload) { encode(buf, payload, RESPONSES); }
    public static CustomPacketPayload decodeResponse(RegistryFriendlyByteBuf buf) { return decode(buf, RESPONSES); }

}
