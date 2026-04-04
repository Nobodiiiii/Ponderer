package com.nododiiiii.ponderer.fabric;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.network.*;
import com.nododiiiii.ponderer.platform.services.NetworkHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric implementation of NetworkHelper using Fabric Networking API.
 */
public class FabricNetworkHelper implements NetworkHelper {

    // Channel IDs
    private static final ResourceLocation UPLOAD_SCENE = new ResourceLocation(Ponderer.MODID, "upload_scene");
    private static final ResourceLocation SYNC_REQUEST = new ResourceLocation(Ponderer.MODID, "sync_request");
    private static final ResourceLocation DOWNLOAD_STRUCTURE = new ResourceLocation(Ponderer.MODID, "download_structure");
    private static final ResourceLocation CAPTURE_BLOCK_ENTITY_NBT_REQUEST = new ResourceLocation(Ponderer.MODID, "capture_block_entity_nbt_request");
    private static final ResourceLocation SYNC_RESPONSE = new ResourceLocation(Ponderer.MODID, "sync_response");
    private static final ResourceLocation DOWNLOAD_STRUCTURE_RESULT = new ResourceLocation(Ponderer.MODID, "download_result");
    private static final ResourceLocation UPLOAD_RESPONSE = new ResourceLocation(Ponderer.MODID, "upload_response");
    private static final ResourceLocation CAPTURE_BLOCK_ENTITY_NBT_RESPONSE = new ResourceLocation(Ponderer.MODID, "capture_block_entity_nbt_response");

    @Override
    public void registerPackets() {
        // Serverbound handlers
        ServerPlayNetworking.registerGlobalReceiver(UPLOAD_SCENE, (server, player, handler, buf, responseSender) -> {
            UploadScenePayload msg = UploadScenePayload.decode(buf);
            server.execute(() -> UploadScenePayload.handle(msg, player));
        });

        ServerPlayNetworking.registerGlobalReceiver(SYNC_REQUEST, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> SyncResponsePayload.sendBatched(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(DOWNLOAD_STRUCTURE, (server, player, handler, buf, responseSender) -> {
            DownloadStructurePayload msg = DownloadStructurePayload.decode(buf);
            server.execute(() -> DownloadStructurePayload.handle(msg, player));
        });

        ServerPlayNetworking.registerGlobalReceiver(CAPTURE_BLOCK_ENTITY_NBT_REQUEST, (server, player, handler, buf, responseSender) -> {
            CaptureBlockEntityNbtRequestPayload msg = CaptureBlockEntityNbtRequestPayload.decode(buf);
            server.execute(() -> CaptureBlockEntityNbtRequestPayload.handle(msg, player));
        });

        // Clientbound handlers
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            registerClientboundHandlers();
        }
    }

    @Environment(EnvType.CLIENT)
    private void registerClientboundHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(SYNC_RESPONSE, (client, handler, buf, responseSender) -> {
            SyncResponsePayload msg = SyncResponsePayload.decode(buf);
            client.execute(() -> SyncResponsePayload.handle(msg));
        });

        ClientPlayNetworking.registerGlobalReceiver(DOWNLOAD_STRUCTURE_RESULT, (client, handler, buf, responseSender) -> {
            DownloadStructureResultPayload msg = DownloadStructureResultPayload.decode(buf);
            client.execute(() -> DownloadStructureResultPayload.handle(msg));
        });

        ClientPlayNetworking.registerGlobalReceiver(UPLOAD_RESPONSE, (client, handler, buf, responseSender) -> {
            UploadResponsePayload msg = UploadResponsePayload.decode(buf);
            client.execute(() -> UploadResponsePayload.handle(msg));
        });

        ClientPlayNetworking.registerGlobalReceiver(CAPTURE_BLOCK_ENTITY_NBT_RESPONSE, (client, handler, buf, responseSender) -> {
            CaptureBlockEntityNbtResponsePayload msg = CaptureBlockEntityNbtResponsePayload.decode(buf);
            client.execute(() -> CaptureBlockEntityNbtResponsePayload.handle(msg));
        });
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void sendToServer(Object packet) {
        net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer());
        ResourceLocation channelId = getChannelId(packet);
        if (!ClientPlayNetworking.canSend(channelId)) {
            return;
        }
        encodePacket(packet, buf);
        ClientPlayNetworking.send(channelId, buf);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, Object packet) {
        net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer());
        ResourceLocation channelId = getChannelId(packet);
        encodePacket(packet, buf);
        ServerPlayNetworking.send(player, channelId, buf);
    }

    private ResourceLocation getChannelId(Object packet) {
        if (packet instanceof UploadScenePayload) return UPLOAD_SCENE;
        if (packet instanceof SyncRequestPayload) return SYNC_REQUEST;
        if (packet instanceof DownloadStructurePayload) return DOWNLOAD_STRUCTURE;
        if (packet instanceof CaptureBlockEntityNbtRequestPayload) return CAPTURE_BLOCK_ENTITY_NBT_REQUEST;
        if (packet instanceof SyncResponsePayload) return SYNC_RESPONSE;
        if (packet instanceof DownloadStructureResultPayload) return DOWNLOAD_STRUCTURE_RESULT;
        if (packet instanceof UploadResponsePayload) return UPLOAD_RESPONSE;
        if (packet instanceof CaptureBlockEntityNbtResponsePayload) return CAPTURE_BLOCK_ENTITY_NBT_RESPONSE;
        throw new IllegalArgumentException("Unknown packet type: " + packet.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    private void encodePacket(Object packet, net.minecraft.network.FriendlyByteBuf buf) {
        if (packet instanceof UploadScenePayload p) p.encode(buf);
        else if (packet instanceof SyncRequestPayload p) p.encode(buf);
        else if (packet instanceof DownloadStructurePayload p) p.encode(buf);
        else if (packet instanceof CaptureBlockEntityNbtRequestPayload p) p.encode(buf);
        else if (packet instanceof SyncResponsePayload p) p.encode(buf);
        else if (packet instanceof DownloadStructureResultPayload p) p.encode(buf);
        else if (packet instanceof UploadResponsePayload p) p.encode(buf);
        else if (packet instanceof CaptureBlockEntityNbtResponsePayload p) p.encode(buf);
        else throw new IllegalArgumentException("Unknown packet type: " + packet.getClass().getName());
    }
}
