package com.nododiiiii.ponderer.neoforge;

import com.nododiiiii.ponderer.network.*;
import com.nododiiiii.ponderer.platform.services.NetworkHelper;
import com.nododiiiii.ponderer.ponder.SceneStore;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

/**
 * NeoForge implementation of NetworkHelper using PayloadRegistrar.
 */
public class NeoForgeNetworkHelper implements NetworkHelper {

    public static final String VERSION = "1";

    /**
     * Called from PondererNeoForge when RegisterPayloadHandlersEvent fires.
     */
    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);

        registrar.playToServer(UploadScenePayload.TYPE, UploadScenePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> UploadScenePayload.handle(payload, (ServerPlayer) ctx.player()));
        });
        registrar.playToServer(SyncRequestPayload.TYPE, SyncRequestPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                ServerPlayer player = (ServerPlayer) ctx.player();
                if (player == null) return;
                List<SyncResponsePayload.FileEntry> scripts = SceneStore.collectServerScripts(player.server);
                List<SyncResponsePayload.FileEntry> structures = SceneStore.collectServerStructures(player.server);
                PacketDistributor.sendToPlayer(player, new SyncResponsePayload(scripts, structures));
            });
        });
        registrar.playToServer(DownloadStructurePayload.TYPE, DownloadStructurePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> DownloadStructurePayload.handle(payload, (ServerPlayer) ctx.player()));
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
