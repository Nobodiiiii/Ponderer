package com.nododiiiii.ponderer.forge;

import com.nododiiiii.ponderer.feedback.ClientFeedback;
import com.nododiiiii.ponderer.feedback.ServerFeedback;
import com.nododiiiii.ponderer.network.*;
import com.nododiiiii.ponderer.network.compat.CustomPacketPayload;
import com.nododiiiii.ponderer.network.compat.StreamCodec;
import com.nododiiiii.ponderer.platform.services.NetworkHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ForgeNetworkHelper implements NetworkHelper {
    private static final String VERSION = "8";
    private static SimpleChannel CHANNEL;
    private static int id;

    @Override public void registerPackets() {
        CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(Ponderer.MODID, "main"),
            () -> VERSION, VERSION::equals, remote -> VERSION.equals(remote)
                || NetworkRegistry.ABSENT.equals(remote) || NetworkRegistry.ACCEPTVANILLA.equals(remote));
        registerServer(FeedbackRequestPayload.class, FeedbackRequestPayload.CODEC, FeedbackRequestPayload::handle);
        registerClient(FeedbackResponsePayload.class, FeedbackResponsePayload.CODEC, ClientFeedback::receive);
        registerClient(FeedbackMessagePayload.class, FeedbackMessagePayload.CODEC, FeedbackMessagePayload::handle);
        registerServer(AutoSyncPreferencePayload.class, AutoSyncPreferencePayload.CODEC, AutoSyncPreferencePayload::handle);
        registerServer(UploadScenePayload.class, UploadScenePayload.CODEC, UploadScenePayload::handle);
        registerServer(SyncRequestPayload.class, SyncRequestPayload.CODEC, SyncRequestPayload::handle);
        registerServer(DownloadStructurePayload.class, DownloadStructurePayload.CODEC, DownloadStructurePayload::handle);
        registerServer(StructurePreviewRequestPayload.class, StructurePreviewRequestPayload.CODEC, StructurePreviewRequestPayload::handle);
        registerServer(CaptureBlockEntityNbtRequestPayload.class, CaptureBlockEntityNbtRequestPayload.CODEC, CaptureBlockEntityNbtRequestPayload::handle);
        registerServer(PermissionListRequestPayload.class, PermissionListRequestPayload.CODEC, PermissionListRequestPayload::handle);
        registerServer(PermissionUpdateRequestPayload.class, PermissionUpdateRequestPayload.CODEC, PermissionUpdateRequestPayload::handle);
        registerServer(BlueprintConfigRequestPayload.class, BlueprintConfigRequestPayload.CODEC, BlueprintConfigRequestPayload::handle);
        registerServer(BlueprintConfigUpdatePayload.class, BlueprintConfigUpdatePayload.CODEC, BlueprintConfigUpdatePayload::handle);
        registerServer(ProjectorConfigUpdatePayload.class, ProjectorConfigUpdatePayload.CODEC, ProjectorConfigUpdatePayload::handle);
        registerServer(ProjectorFeatureConfigRequestPayload.class, ProjectorFeatureConfigRequestPayload.CODEC, ProjectorFeatureConfigRequestPayload::handle);
        registerServer(ProjectorFeatureConfigUpdatePayload.class, ProjectorFeatureConfigUpdatePayload.CODEC, ProjectorFeatureConfigUpdatePayload::handle);
        registerServer(RemoteCatalogRequestPayload.class, RemoteCatalogRequestPayload.CODEC, RemoteCatalogRequestPayload::handle);
        registerServer(RemotePullRequestPayload.class, RemotePullRequestPayload.CODEC, RemotePullRequestPayload::handle);
        registerServer(RemoteDeleteRequestPayload.class, RemoteDeleteRequestPayload.CODEC, RemoteDeleteRequestPayload::handle);
        registerServer(RemoteHistoryRequestPayload.class, RemoteHistoryRequestPayload.CODEC, RemoteHistoryRequestPayload::handle);
        registerServer(RemoteRollbackRequestPayload.class, RemoteRollbackRequestPayload.CODEC, RemoteRollbackRequestPayload::handle);
        registerClient(SyncResponsePayload.class, SyncResponsePayload.CODEC, SyncResponsePayload::handle);
        registerClient(DownloadStructureResultPayload.class, DownloadStructureResultPayload.CODEC, DownloadStructureResultPayload::handle);
        registerClient(StructurePreviewResponsePayload.class, StructurePreviewResponsePayload.CODEC, StructurePreviewResponsePayload::handle);
        registerClient(UploadResponsePayload.class, UploadResponsePayload.CODEC, UploadResponsePayload::handle);
        registerClient(CaptureBlockEntityNbtResponsePayload.class, CaptureBlockEntityNbtResponsePayload.CODEC, CaptureBlockEntityNbtResponsePayload::handle);
        registerClient(PermissionListResponsePayload.class, PermissionListResponsePayload.CODEC, PermissionListResponsePayload::handle);
        registerClient(BlueprintConfigResponsePayload.class, BlueprintConfigResponsePayload.CODEC, BlueprintConfigResponsePayload::handle);
        registerClient(ProjectorFeatureConfigResponsePayload.class, ProjectorFeatureConfigResponsePayload.CODEC, ProjectorFeatureConfigResponsePayload::handle);
        registerClient(ProjectorPlaybackStartPayload.class, ProjectorPlaybackStartPayload.CODEC, ProjectorPlaybackStartPayload::handle);
        registerClient(FeatureAvailabilityPayload.class, FeatureAvailabilityPayload.CODEC, FeatureAvailabilityPayload::handle);
        registerClient(RemoteCatalogResponsePayload.class, RemoteCatalogResponsePayload.CODEC, RemoteCatalogResponsePayload::handle);
        registerClient(RemoteActionResponsePayload.class, RemoteActionResponsePayload.CODEC, RemoteActionResponsePayload::handle);
        registerClient(RemoteHistoryResponsePayload.class, RemoteHistoryResponsePayload.CODEC, RemoteHistoryResponsePayload::handle);
    }
    private <T extends CustomPacketPayload> void registerServer(Class<T> type, StreamCodec<FriendlyByteBuf, T> codec,
                                                               BiConsumer<T, ServerPlayer> handler) {
        CHANNEL.messageBuilder(type, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder((payload, buf) -> codec.encode(buf, payload)).decoder(codec::decode)
            .consumerMainThread((payload, ctx) -> {
                handler.accept(payload, ctx.get().getSender());
                ctx.get().setPacketHandled(true);
            }).add();
    }
    private <T extends CustomPacketPayload> void registerClient(Class<T> type, StreamCodec<FriendlyByteBuf, T> codec,
                                                               Consumer<T> handler) {
        CHANNEL.messageBuilder(type, id++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder((payload, buf) -> codec.encode(buf, payload)).decoder(codec::decode)
            .consumerMainThread((payload, ctx) -> {
                handler.accept(payload);
                ctx.get().setPacketHandled(true);
            }).add();
    }
    @Override public void sendToServer(CustomPacketPayload packet) { CHANNEL.sendToServer(ClientFeedback.request(packet)); }
    @Override public void sendToPlayer(ServerPlayer player, CustomPacketPayload packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), ServerFeedback.response(player, packet));
    }
    @Override public boolean canSendToServer(CustomPacketPayload.Type<?> type) {
        var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
        return CHANNEL != null && connection != null && CHANNEL.isRemotePresent(connection.getConnection());
    }
}
