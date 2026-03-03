package com.nododiiiii.ponderer.fabric;

import com.nododiiiii.ponderer.network.*;
import com.nododiiiii.ponderer.platform.services.NetworkHelper;
import com.nododiiiii.ponderer.ponder.SceneStore;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Fabric implementation of NetworkHelper using Fabric Networking API v1 (1.21.1+).
 */
public class FabricNetworkHelper implements NetworkHelper {

    @Override
    public void registerPackets() {
        // Register payload types
        PayloadTypeRegistry.playC2S().register(UploadScenePayload.TYPE, UploadScenePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SyncRequestPayload.TYPE, SyncRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DownloadStructurePayload.TYPE, DownloadStructurePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncResponsePayload.TYPE, SyncResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DownloadStructureResultPayload.TYPE, DownloadStructureResultPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(UploadResponsePayload.TYPE, UploadResponsePayload.CODEC);

        // Server-bound handlers
        ServerPlayNetworking.registerGlobalReceiver(UploadScenePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> UploadScenePayload.handle(payload, player));
        });

        ServerPlayNetworking.registerGlobalReceiver(SyncRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> {
                List<SyncResponsePayload.FileEntry> scripts = SceneStore.collectServerScripts(player.server);
                List<SyncResponsePayload.FileEntry> structures = SceneStore.collectServerStructures(player.server);
                sendToPlayer(player, new SyncResponsePayload(scripts, structures));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DownloadStructurePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.player().server.execute(() -> DownloadStructurePayload.handle(payload, player));
        });

        // Client-bound handlers
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
