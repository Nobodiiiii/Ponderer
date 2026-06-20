package com.nododiiiii.ponderer.neoforge;

import com.nododiiiii.ponderer.network.*;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.network.MirrorClosePacket;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.network.MirrorNeoForgeOpenPacket;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.network.ReplayItemSnapshotPacket;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.network.ReplaySnapshotPacket;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.network.ReplayUiIdPacket;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.network.SaveItemSnapshotPacket;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.network.SaveSnapshotPacket;
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

    public static final String VERSION = "2";

    /**
     * Called from PondererNeoForge when RegisterPayloadHandlersEvent fires.
     */
    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);

        registrar.playToServer(UploadScenePayload.TYPE, UploadScenePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> UploadScenePayload.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(SyncRequestPayload.TYPE, SyncRequestPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> SyncRequestPayload.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(DownloadStructurePayload.TYPE, DownloadStructurePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> DownloadStructurePayload.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(CaptureBlockEntityNbtRequestPayload.TYPE, CaptureBlockEntityNbtRequestPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> CaptureBlockEntityNbtRequestPayload.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(PermissionListRequestPayload.TYPE, PermissionListRequestPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> PermissionListRequestPayload.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(PermissionUpdateRequestPayload.TYPE, PermissionUpdateRequestPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> PermissionUpdateRequestPayload.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(BlueprintConfigRequestPayload.TYPE, BlueprintConfigRequestPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> BlueprintConfigRequestPayload.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(BlueprintConfigUpdatePayload.TYPE, BlueprintConfigUpdatePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> BlueprintConfigUpdatePayload.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(ProjectorConfigUpdatePayload.TYPE, ProjectorConfigUpdatePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> ProjectorConfigUpdatePayload.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(ProjectorManualTriggerPayload.TYPE, ProjectorManualTriggerPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> ProjectorManualTriggerPayload.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(ProjectorSeekPayload.TYPE, ProjectorSeekPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> ProjectorSeekPayload.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(ProjectorFeatureConfigRequestPayload.TYPE, ProjectorFeatureConfigRequestPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> ProjectorFeatureConfigRequestPayload.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(ProjectorFeatureConfigUpdatePayload.TYPE, ProjectorFeatureConfigUpdatePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> ProjectorFeatureConfigUpdatePayload.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(RemoteCatalogRequestPayload.TYPE, RemoteCatalogRequestPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> RemoteCatalogRequestPayload.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(RemotePullRequestPayload.TYPE, RemotePullRequestPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> RemotePullRequestPayload.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(RemoteDeleteRequestPayload.TYPE, RemoteDeleteRequestPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> RemoteDeleteRequestPayload.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(RemoteHistoryRequestPayload.TYPE, RemoteHistoryRequestPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> RemoteHistoryRequestPayload.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(RemoteRollbackRequestPayload.TYPE, RemoteRollbackRequestPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> RemoteRollbackRequestPayload.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(SaveSnapshotPacket.TYPE, SaveSnapshotPacket.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> SaveSnapshotPacket.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(ReplaySnapshotPacket.TYPE, ReplaySnapshotPacket.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> ReplaySnapshotPacket.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(MirrorClosePacket.TYPE, MirrorClosePacket.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> MirrorClosePacket.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(SaveItemSnapshotPacket.TYPE, SaveItemSnapshotPacket.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> SaveItemSnapshotPacket.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(ReplayItemSnapshotPacket.TYPE, ReplayItemSnapshotPacket.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> ReplayItemSnapshotPacket.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(ReplayUiIdPacket.TYPE, ReplayUiIdPacket.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> ReplayUiIdPacket.handle(payload, (ServerPlayer) ctx.player()));
        });

        registrar.playToClient(SyncResponsePayload.TYPE, SyncResponsePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> SyncResponsePayload.handle(payload));
        });
        registrar.playToClient(DownloadStructureResultPayload.TYPE, DownloadStructureResultPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> DownloadStructureResultPayload.handle(payload));
        });
        registrar.playToClient(UploadResponsePayload.TYPE, UploadResponsePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> UploadResponsePayload.handle(payload));
        });
        registrar.playToClient(CaptureBlockEntityNbtResponsePayload.TYPE, CaptureBlockEntityNbtResponsePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> CaptureBlockEntityNbtResponsePayload.handle(payload));
        });
        registrar.playToClient(PermissionListResponsePayload.TYPE, PermissionListResponsePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> PermissionListResponsePayload.handle(payload));
        });
        registrar.playToClient(BlueprintConfigResponsePayload.TYPE, BlueprintConfigResponsePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> BlueprintConfigResponsePayload.handle(payload));
        });
        registrar.playToClient(ProjectorFeatureConfigResponsePayload.TYPE, ProjectorFeatureConfigResponsePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> ProjectorFeatureConfigResponsePayload.handle(payload));
        });
        registrar.playToClient(FeatureAvailabilityPayload.TYPE, FeatureAvailabilityPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> FeatureAvailabilityPayload.handle(payload));
        });
        registrar.playToClient(RemoteCatalogResponsePayload.TYPE, RemoteCatalogResponsePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> RemoteCatalogResponsePayload.handle(payload));
        });
        registrar.playToClient(RemoteActionResponsePayload.TYPE, RemoteActionResponsePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> RemoteActionResponsePayload.handle(payload));
        });
        registrar.playToClient(RemoteHistoryResponsePayload.TYPE, RemoteHistoryResponsePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> RemoteHistoryResponsePayload.handle(payload));
        });
        registrar.playToClient(MirrorNeoForgeOpenPacket.TYPE, MirrorNeoForgeOpenPacket.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> MirrorNeoForgeOpenPacket.handle(payload));
        });
    }

    @Override
    public void registerPackets() {
        // No-op for NeoForge; done via registerPayloads() called from the entry point.
    }

    @Override
    public void sendToServer(CustomPacketPayload packet) {
        PacketDistributor.sendToServer(packet);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }
}
