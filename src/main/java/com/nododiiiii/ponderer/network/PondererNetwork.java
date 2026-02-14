package com.nododiiiii.ponderer.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import com.nododiiiii.ponderer.ponder.SceneStore;
import java.util.List;

public final class PondererNetwork {
    public static final String VERSION = "1";

    private PondererNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
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
}