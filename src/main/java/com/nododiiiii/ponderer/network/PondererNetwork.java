package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.ponder.SceneStore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;

public final class PondererNetwork {
    private static final String VERSION = "1";
    public static SimpleChannel CHANNEL;
    private static int id = 0;

    private PondererNetwork() {
    }

    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Ponderer.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals
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
            .encoder((msg, buf) -> {})
            .decoder(buf -> new SyncRequestPayload())
            .consumerMainThread((msg, ctx) -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                List<SyncResponsePayload.FileEntry> scripts = SceneStore.collectServerScripts(player.server);
                List<SyncResponsePayload.FileEntry> structures = SceneStore.collectServerStructures(player.server);
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncResponsePayload(scripts, structures));
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
    }
}
