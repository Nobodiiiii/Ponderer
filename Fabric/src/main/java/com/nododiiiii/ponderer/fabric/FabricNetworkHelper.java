package com.nododiiiii.ponderer.fabric;

import com.nododiiiii.ponderer.network.*;
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
        PayloadTypeRegistry.playC2S().register(UploadScenePayload.TYPE, UploadScenePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SyncRequestPayload.TYPE, SyncRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DownloadStructurePayload.TYPE, DownloadStructurePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CaptureBlockEntityNbtRequestPayload.TYPE, CaptureBlockEntityNbtRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PermissionListRequestPayload.TYPE, PermissionListRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PermissionUpdateRequestPayload.TYPE, PermissionUpdateRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BlueprintConfigRequestPayload.TYPE, BlueprintConfigRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BlueprintConfigUpdatePayload.TYPE, BlueprintConfigUpdatePayload.CODEC);

        PayloadTypeRegistry.playS2C().register(SyncResponsePayload.TYPE, SyncResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DownloadStructureResultPayload.TYPE, DownloadStructureResultPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(UploadResponsePayload.TYPE, UploadResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CaptureBlockEntityNbtResponsePayload.TYPE, CaptureBlockEntityNbtResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PermissionListResponsePayload.TYPE, PermissionListResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BlueprintConfigResponsePayload.TYPE, BlueprintConfigResponsePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(UploadScenePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> UploadScenePayload.handle(payload, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(SyncRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> SyncRequestPayload.handle(payload, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(DownloadStructurePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> DownloadStructurePayload.handle(payload, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(CaptureBlockEntityNbtRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> CaptureBlockEntityNbtRequestPayload.handle(payload, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(PermissionListRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> PermissionListRequestPayload.handle(payload, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(PermissionUpdateRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> PermissionUpdateRequestPayload.handle(payload, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(BlueprintConfigRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> BlueprintConfigRequestPayload.handle(payload, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(BlueprintConfigUpdatePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> BlueprintConfigUpdatePayload.handle(payload, player));
        });

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            registerClientHandlers();
        }
    }

    @Environment(EnvType.CLIENT)
    private void registerClientHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(SyncResponsePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> SyncResponsePayload.handle(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(DownloadStructureResultPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> DownloadStructureResultPayload.handle(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(UploadResponsePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> UploadResponsePayload.handle(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(CaptureBlockEntityNbtResponsePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> CaptureBlockEntityNbtResponsePayload.handle(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(PermissionListResponsePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> PermissionListResponsePayload.handle(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(BlueprintConfigResponsePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> BlueprintConfigResponsePayload.handle(payload));
        });
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void sendToServer(CustomPacketPayload packet) {
        ClientPlayNetworking.send(packet);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload packet) {
        ServerPlayNetworking.send(player, packet);
    }
}
