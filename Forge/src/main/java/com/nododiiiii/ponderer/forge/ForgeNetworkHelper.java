package com.nododiiiii.ponderer.forge;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.network.*;
import com.nododiiiii.ponderer.platform.services.NetworkHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Forge implementation of NetworkHelper using SimpleChannel.
 */
public class ForgeNetworkHelper implements NetworkHelper {

    private static final String VERSION = "5";
    private static SimpleChannel CHANNEL;
    private static int id = 0;

    @Override
    public void registerPackets() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(Ponderer.MODID, "main"),
            () -> VERSION,
            VERSION::equals,
            remoteVersion -> VERSION.equals(remoteVersion)
                || NetworkRegistry.ABSENT.equals(remoteVersion)
                || NetworkRegistry.ACCEPTVANILLA.equals(remoteVersion)
        );

        // Client -> Server
        CHANNEL.messageBuilder(UploadScenePayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UploadScenePayload::encode)
                .decoder(UploadScenePayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    UploadScenePayload.handle(msg, player);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(SyncRequestPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SyncRequestPayload::encode)
                .decoder(SyncRequestPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    SyncRequestPayload.handle(msg, player);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(DownloadStructurePayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DownloadStructurePayload::encode)
                .decoder(DownloadStructurePayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    DownloadStructurePayload.handle(msg, player);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(CaptureBlockEntityNbtRequestPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CaptureBlockEntityNbtRequestPayload::encode)
                .decoder(CaptureBlockEntityNbtRequestPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    CaptureBlockEntityNbtRequestPayload.handle(msg, player);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(PermissionListRequestPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PermissionListRequestPayload::encode)
                .decoder(PermissionListRequestPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    PermissionListRequestPayload.handle(msg, player);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(PermissionUpdateRequestPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PermissionUpdateRequestPayload::encode)
                .decoder(PermissionUpdateRequestPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    PermissionUpdateRequestPayload.handle(msg, player);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(BlueprintConfigRequestPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BlueprintConfigRequestPayload::encode)
                .decoder(BlueprintConfigRequestPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    BlueprintConfigRequestPayload.handle(msg, player);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(BlueprintConfigUpdatePayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BlueprintConfigUpdatePayload::encode)
                .decoder(BlueprintConfigUpdatePayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    BlueprintConfigUpdatePayload.handle(msg, player);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(ProjectorConfigUpdatePayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ProjectorConfigUpdatePayload::encode)
                .decoder(ProjectorConfigUpdatePayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    ProjectorConfigUpdatePayload.handle(msg, player);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(ProjectorManualTriggerPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ProjectorManualTriggerPayload::encode)
                .decoder(ProjectorManualTriggerPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    ProjectorManualTriggerPayload.handle(msg, player);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(ProjectorSeekPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ProjectorSeekPayload::encode)
                .decoder(ProjectorSeekPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    ProjectorSeekPayload.handle(msg, player);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(RemoteCatalogRequestPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RemoteCatalogRequestPayload::encode)
                .decoder(RemoteCatalogRequestPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    RemoteCatalogRequestPayload.handle(msg, player);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(RemotePullRequestPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RemotePullRequestPayload::encode)
                .decoder(RemotePullRequestPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    RemotePullRequestPayload.handle(msg, player);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(RemoteDeleteRequestPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RemoteDeleteRequestPayload::encode)
                .decoder(RemoteDeleteRequestPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    RemoteDeleteRequestPayload.handle(msg, player);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(RemoteHistoryRequestPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RemoteHistoryRequestPayload::encode)
                .decoder(RemoteHistoryRequestPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    RemoteHistoryRequestPayload.handle(msg, player);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(RemoteRollbackRequestPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RemoteRollbackRequestPayload::encode)
                .decoder(RemoteRollbackRequestPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    RemoteRollbackRequestPayload.handle(msg, player);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        // Server -> Client
        CHANNEL.messageBuilder(SyncResponsePayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncResponsePayload::encode)
                .decoder(SyncResponsePayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    SyncResponsePayload.handle(msg);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(DownloadStructureResultPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DownloadStructureResultPayload::encode)
                .decoder(DownloadStructureResultPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    DownloadStructureResultPayload.handle(msg);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(UploadResponsePayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(UploadResponsePayload::encode)
                .decoder(UploadResponsePayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    UploadResponsePayload.handle(msg);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(CaptureBlockEntityNbtResponsePayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CaptureBlockEntityNbtResponsePayload::encode)
                .decoder(CaptureBlockEntityNbtResponsePayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    CaptureBlockEntityNbtResponsePayload.handle(msg);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(PermissionListResponsePayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PermissionListResponsePayload::encode)
                .decoder(PermissionListResponsePayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    PermissionListResponsePayload.handle(msg);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(BlueprintConfigResponsePayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BlueprintConfigResponsePayload::encode)
                .decoder(BlueprintConfigResponsePayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    BlueprintConfigResponsePayload.handle(msg);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(ProjectorFeatureConfigRequestPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ProjectorFeatureConfigRequestPayload::encode)
                .decoder(ProjectorFeatureConfigRequestPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    ProjectorFeatureConfigRequestPayload.handle(msg, player);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(ProjectorFeatureConfigUpdatePayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ProjectorFeatureConfigUpdatePayload::encode)
                .decoder(ProjectorFeatureConfigUpdatePayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    ProjectorFeatureConfigUpdatePayload.handle(msg, player);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(ProjectorFeatureConfigResponsePayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ProjectorFeatureConfigResponsePayload::encode)
                .decoder(ProjectorFeatureConfigResponsePayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ProjectorFeatureConfigResponsePayload.handle(msg);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(FeatureAvailabilityPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FeatureAvailabilityPayload::encode)
                .decoder(FeatureAvailabilityPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    FeatureAvailabilityPayload.handle(msg);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(RemoteCatalogResponsePayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RemoteCatalogResponsePayload::encode)
                .decoder(RemoteCatalogResponsePayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    RemoteCatalogResponsePayload.handle(msg);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(RemoteActionResponsePayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RemoteActionResponsePayload::encode)
                .decoder(RemoteActionResponsePayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    RemoteActionResponsePayload.handle(msg);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(RemoteHistoryResponsePayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RemoteHistoryResponsePayload::encode)
                .decoder(RemoteHistoryResponsePayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    RemoteHistoryResponsePayload.handle(msg);
                    ctx.get().setPacketHandled(true);
                })
                .add();
    }

    @Override
    public void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
