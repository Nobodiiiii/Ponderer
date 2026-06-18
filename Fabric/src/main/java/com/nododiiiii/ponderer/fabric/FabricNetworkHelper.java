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
    private static final ResourceLocation PERMISSION_LIST_REQUEST = new ResourceLocation(Ponderer.MODID, "permission_list_request");
    private static final ResourceLocation PERMISSION_UPDATE = new ResourceLocation(Ponderer.MODID, "permission_update");
    private static final ResourceLocation BLUEPRINT_CONFIG_REQUEST = new ResourceLocation(Ponderer.MODID, "blueprint_config_request");
    private static final ResourceLocation BLUEPRINT_CONFIG_UPDATE = new ResourceLocation(Ponderer.MODID, "blueprint_config_update");
    private static final ResourceLocation PROJECTOR_CONFIG_UPDATE = new ResourceLocation(Ponderer.MODID, "projector_config_update");
    private static final ResourceLocation PROJECTOR_MANUAL_TRIGGER = new ResourceLocation(Ponderer.MODID, "projector_manual_trigger");
    private static final ResourceLocation PROJECTOR_SEEK = new ResourceLocation(Ponderer.MODID, "projector_seek");
    private static final ResourceLocation PROJECTOR_FEATURE_CONFIG_REQUEST = new ResourceLocation(Ponderer.MODID, "projector_feature_config_request");
    private static final ResourceLocation PROJECTOR_FEATURE_CONFIG_UPDATE = new ResourceLocation(Ponderer.MODID, "projector_feature_config_update");
    private static final ResourceLocation REMOTE_CATALOG_REQUEST = new ResourceLocation(Ponderer.MODID, "remote_catalog_request");
    private static final ResourceLocation REMOTE_PULL_REQUEST = new ResourceLocation(Ponderer.MODID, "remote_pull_request");
    private static final ResourceLocation REMOTE_DELETE_REQUEST = new ResourceLocation(Ponderer.MODID, "remote_delete_request");
    private static final ResourceLocation REMOTE_HISTORY_REQUEST = new ResourceLocation(Ponderer.MODID, "remote_history_request");
    private static final ResourceLocation REMOTE_ROLLBACK_REQUEST = new ResourceLocation(Ponderer.MODID, "remote_rollback_request");
    private static final ResourceLocation SYNC_RESPONSE = new ResourceLocation(Ponderer.MODID, "sync_response");
    private static final ResourceLocation DOWNLOAD_STRUCTURE_RESULT = new ResourceLocation(Ponderer.MODID, "download_result");
    private static final ResourceLocation UPLOAD_RESPONSE = new ResourceLocation(Ponderer.MODID, "upload_response");
    private static final ResourceLocation CAPTURE_BLOCK_ENTITY_NBT_RESPONSE = new ResourceLocation(Ponderer.MODID, "capture_block_entity_nbt_response");
    private static final ResourceLocation PERMISSION_LIST_RESPONSE = new ResourceLocation(Ponderer.MODID, "permission_list_response");
    private static final ResourceLocation BLUEPRINT_CONFIG_RESPONSE = new ResourceLocation(Ponderer.MODID, "blueprint_config_response");
    private static final ResourceLocation PROJECTOR_FEATURE_CONFIG_RESPONSE = new ResourceLocation(Ponderer.MODID, "projector_feature_config_response");
    private static final ResourceLocation FEATURE_AVAILABILITY = new ResourceLocation(Ponderer.MODID, "feature_availability");
    private static final ResourceLocation REMOTE_CATALOG_RESPONSE = new ResourceLocation(Ponderer.MODID, "remote_catalog_response");
    private static final ResourceLocation REMOTE_ACTION_RESPONSE = new ResourceLocation(Ponderer.MODID, "remote_action_response");
    private static final ResourceLocation REMOTE_HISTORY_RESPONSE = new ResourceLocation(Ponderer.MODID, "remote_history_response");

    @Override
    public void registerPackets() {
        // Serverbound handlers
        ServerPlayNetworking.registerGlobalReceiver(UPLOAD_SCENE, (server, player, handler, buf, responseSender) -> {
            UploadScenePayload msg = UploadScenePayload.decode(buf);
            server.execute(() -> UploadScenePayload.handle(msg, player));
        });

        ServerPlayNetworking.registerGlobalReceiver(SYNC_REQUEST, (server, player, handler, buf, responseSender) -> {
            SyncRequestPayload msg = SyncRequestPayload.decode(buf);
            server.execute(() -> SyncRequestPayload.handle(msg, player));
        });

        ServerPlayNetworking.registerGlobalReceiver(DOWNLOAD_STRUCTURE, (server, player, handler, buf, responseSender) -> {
            DownloadStructurePayload msg = DownloadStructurePayload.decode(buf);
            server.execute(() -> DownloadStructurePayload.handle(msg, player));
        });

        ServerPlayNetworking.registerGlobalReceiver(CAPTURE_BLOCK_ENTITY_NBT_REQUEST, (server, player, handler, buf, responseSender) -> {
            CaptureBlockEntityNbtRequestPayload msg = CaptureBlockEntityNbtRequestPayload.decode(buf);
            server.execute(() -> CaptureBlockEntityNbtRequestPayload.handle(msg, player));
        });

        ServerPlayNetworking.registerGlobalReceiver(PERMISSION_LIST_REQUEST, (server, player, handler, buf, responseSender) -> {
            PermissionListRequestPayload msg = PermissionListRequestPayload.decode(buf);
            server.execute(() -> PermissionListRequestPayload.handle(msg, player));
        });

        ServerPlayNetworking.registerGlobalReceiver(PERMISSION_UPDATE, (server, player, handler, buf, responseSender) -> {
            PermissionUpdateRequestPayload msg = PermissionUpdateRequestPayload.decode(buf);
            server.execute(() -> PermissionUpdateRequestPayload.handle(msg, player));
        });

        ServerPlayNetworking.registerGlobalReceiver(BLUEPRINT_CONFIG_REQUEST, (server, player, handler, buf, responseSender) -> {
            BlueprintConfigRequestPayload msg = BlueprintConfigRequestPayload.decode(buf);
            server.execute(() -> BlueprintConfigRequestPayload.handle(msg, player));
        });

        ServerPlayNetworking.registerGlobalReceiver(BLUEPRINT_CONFIG_UPDATE, (server, player, handler, buf, responseSender) -> {
            BlueprintConfigUpdatePayload msg = BlueprintConfigUpdatePayload.decode(buf);
            server.execute(() -> BlueprintConfigUpdatePayload.handle(msg, player));
        });

        ServerPlayNetworking.registerGlobalReceiver(PROJECTOR_CONFIG_UPDATE, (server, player, handler, buf, responseSender) -> {
            ProjectorConfigUpdatePayload msg = ProjectorConfigUpdatePayload.decode(buf);
            server.execute(() -> ProjectorConfigUpdatePayload.handle(msg, player));
        });

        ServerPlayNetworking.registerGlobalReceiver(PROJECTOR_MANUAL_TRIGGER, (server, player, handler, buf, responseSender) -> {
            ProjectorManualTriggerPayload msg = ProjectorManualTriggerPayload.decode(buf);
            server.execute(() -> ProjectorManualTriggerPayload.handle(msg, player));
        });

        ServerPlayNetworking.registerGlobalReceiver(PROJECTOR_SEEK, (server, player, handler, buf, responseSender) -> {
            ProjectorSeekPayload msg = ProjectorSeekPayload.decode(buf);
            server.execute(() -> ProjectorSeekPayload.handle(msg, player));
        });

        ServerPlayNetworking.registerGlobalReceiver(PROJECTOR_FEATURE_CONFIG_REQUEST, (server, player, handler, buf, responseSender) -> {
            ProjectorFeatureConfigRequestPayload msg = ProjectorFeatureConfigRequestPayload.decode(buf);
            server.execute(() -> ProjectorFeatureConfigRequestPayload.handle(msg, player));
        });

        ServerPlayNetworking.registerGlobalReceiver(PROJECTOR_FEATURE_CONFIG_UPDATE, (server, player, handler, buf, responseSender) -> {
            ProjectorFeatureConfigUpdatePayload msg = ProjectorFeatureConfigUpdatePayload.decode(buf);
            server.execute(() -> ProjectorFeatureConfigUpdatePayload.handle(msg, player));
        });

        ServerPlayNetworking.registerGlobalReceiver(REMOTE_CATALOG_REQUEST, (server, player, handler, buf, responseSender) -> {
            RemoteCatalogRequestPayload msg = RemoteCatalogRequestPayload.decode(buf);
            server.execute(() -> RemoteCatalogRequestPayload.handle(msg, player));
        });

        ServerPlayNetworking.registerGlobalReceiver(REMOTE_PULL_REQUEST, (server, player, handler, buf, responseSender) -> {
            RemotePullRequestPayload msg = RemotePullRequestPayload.decode(buf);
            server.execute(() -> RemotePullRequestPayload.handle(msg, player));
        });

        ServerPlayNetworking.registerGlobalReceiver(REMOTE_DELETE_REQUEST, (server, player, handler, buf, responseSender) -> {
            RemoteDeleteRequestPayload msg = RemoteDeleteRequestPayload.decode(buf);
            server.execute(() -> RemoteDeleteRequestPayload.handle(msg, player));
        });

        ServerPlayNetworking.registerGlobalReceiver(REMOTE_HISTORY_REQUEST, (server, player, handler, buf, responseSender) -> {
            RemoteHistoryRequestPayload msg = RemoteHistoryRequestPayload.decode(buf);
            server.execute(() -> RemoteHistoryRequestPayload.handle(msg, player));
        });

        ServerPlayNetworking.registerGlobalReceiver(REMOTE_ROLLBACK_REQUEST, (server, player, handler, buf, responseSender) -> {
            RemoteRollbackRequestPayload msg = RemoteRollbackRequestPayload.decode(buf);
            server.execute(() -> RemoteRollbackRequestPayload.handle(msg, player));
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

        ClientPlayNetworking.registerGlobalReceiver(PERMISSION_LIST_RESPONSE, (client, handler, buf, responseSender) -> {
            PermissionListResponsePayload msg = PermissionListResponsePayload.decode(buf);
            client.execute(() -> PermissionListResponsePayload.handle(msg));
        });

        ClientPlayNetworking.registerGlobalReceiver(BLUEPRINT_CONFIG_RESPONSE, (client, handler, buf, responseSender) -> {
            BlueprintConfigResponsePayload msg = BlueprintConfigResponsePayload.decode(buf);
            client.execute(() -> BlueprintConfigResponsePayload.handle(msg));
        });

        ClientPlayNetworking.registerGlobalReceiver(PROJECTOR_FEATURE_CONFIG_RESPONSE, (client, handler, buf, responseSender) -> {
            ProjectorFeatureConfigResponsePayload msg = ProjectorFeatureConfigResponsePayload.decode(buf);
            client.execute(() -> ProjectorFeatureConfigResponsePayload.handle(msg));
        });

        ClientPlayNetworking.registerGlobalReceiver(FEATURE_AVAILABILITY, (client, handler, buf, responseSender) -> {
            FeatureAvailabilityPayload msg = FeatureAvailabilityPayload.decode(buf);
            client.execute(() -> FeatureAvailabilityPayload.handle(msg));
        });

        ClientPlayNetworking.registerGlobalReceiver(REMOTE_CATALOG_RESPONSE, (client, handler, buf, responseSender) -> {
            RemoteCatalogResponsePayload msg = RemoteCatalogResponsePayload.decode(buf);
            client.execute(() -> RemoteCatalogResponsePayload.handle(msg));
        });

        ClientPlayNetworking.registerGlobalReceiver(REMOTE_ACTION_RESPONSE, (client, handler, buf, responseSender) -> {
            RemoteActionResponsePayload msg = RemoteActionResponsePayload.decode(buf);
            client.execute(() -> RemoteActionResponsePayload.handle(msg));
        });

        ClientPlayNetworking.registerGlobalReceiver(REMOTE_HISTORY_RESPONSE, (client, handler, buf, responseSender) -> {
            RemoteHistoryResponsePayload msg = RemoteHistoryResponsePayload.decode(buf);
            client.execute(() -> RemoteHistoryResponsePayload.handle(msg));
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
        if (packet instanceof PermissionListRequestPayload) return PERMISSION_LIST_REQUEST;
        if (packet instanceof PermissionUpdateRequestPayload) return PERMISSION_UPDATE;
        if (packet instanceof BlueprintConfigRequestPayload) return BLUEPRINT_CONFIG_REQUEST;
        if (packet instanceof BlueprintConfigUpdatePayload) return BLUEPRINT_CONFIG_UPDATE;
        if (packet instanceof ProjectorConfigUpdatePayload) return PROJECTOR_CONFIG_UPDATE;
        if (packet instanceof ProjectorManualTriggerPayload) return PROJECTOR_MANUAL_TRIGGER;
        if (packet instanceof ProjectorSeekPayload) return PROJECTOR_SEEK;
        if (packet instanceof ProjectorFeatureConfigRequestPayload) return PROJECTOR_FEATURE_CONFIG_REQUEST;
        if (packet instanceof ProjectorFeatureConfigUpdatePayload) return PROJECTOR_FEATURE_CONFIG_UPDATE;
        if (packet instanceof RemoteCatalogRequestPayload) return REMOTE_CATALOG_REQUEST;
        if (packet instanceof RemotePullRequestPayload) return REMOTE_PULL_REQUEST;
        if (packet instanceof RemoteDeleteRequestPayload) return REMOTE_DELETE_REQUEST;
        if (packet instanceof RemoteHistoryRequestPayload) return REMOTE_HISTORY_REQUEST;
        if (packet instanceof RemoteRollbackRequestPayload) return REMOTE_ROLLBACK_REQUEST;
        if (packet instanceof SyncResponsePayload) return SYNC_RESPONSE;
        if (packet instanceof DownloadStructureResultPayload) return DOWNLOAD_STRUCTURE_RESULT;
        if (packet instanceof UploadResponsePayload) return UPLOAD_RESPONSE;
        if (packet instanceof CaptureBlockEntityNbtResponsePayload) return CAPTURE_BLOCK_ENTITY_NBT_RESPONSE;
        if (packet instanceof PermissionListResponsePayload) return PERMISSION_LIST_RESPONSE;
        if (packet instanceof BlueprintConfigResponsePayload) return BLUEPRINT_CONFIG_RESPONSE;
        if (packet instanceof ProjectorFeatureConfigResponsePayload) return PROJECTOR_FEATURE_CONFIG_RESPONSE;
        if (packet instanceof FeatureAvailabilityPayload) return FEATURE_AVAILABILITY;
        if (packet instanceof RemoteCatalogResponsePayload) return REMOTE_CATALOG_RESPONSE;
        if (packet instanceof RemoteActionResponsePayload) return REMOTE_ACTION_RESPONSE;
        if (packet instanceof RemoteHistoryResponsePayload) return REMOTE_HISTORY_RESPONSE;
        throw new IllegalArgumentException("Unknown packet type: " + packet.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    private void encodePacket(Object packet, net.minecraft.network.FriendlyByteBuf buf) {
        if (packet instanceof UploadScenePayload p) p.encode(buf);
        else if (packet instanceof SyncRequestPayload p) p.encode(buf);
        else if (packet instanceof DownloadStructurePayload p) p.encode(buf);
        else if (packet instanceof CaptureBlockEntityNbtRequestPayload p) p.encode(buf);
        else if (packet instanceof PermissionListRequestPayload p) p.encode(buf);
        else if (packet instanceof PermissionUpdateRequestPayload p) p.encode(buf);
        else if (packet instanceof BlueprintConfigRequestPayload p) p.encode(buf);
        else if (packet instanceof BlueprintConfigUpdatePayload p) p.encode(buf);
        else if (packet instanceof ProjectorConfigUpdatePayload p) p.encode(buf);
        else if (packet instanceof ProjectorManualTriggerPayload p) p.encode(buf);
        else if (packet instanceof ProjectorSeekPayload p) p.encode(buf);
        else if (packet instanceof ProjectorFeatureConfigRequestPayload p) p.encode(buf);
        else if (packet instanceof ProjectorFeatureConfigUpdatePayload p) p.encode(buf);
        else if (packet instanceof RemoteCatalogRequestPayload p) p.encode(buf);
        else if (packet instanceof RemotePullRequestPayload p) p.encode(buf);
        else if (packet instanceof RemoteDeleteRequestPayload p) p.encode(buf);
        else if (packet instanceof RemoteHistoryRequestPayload p) p.encode(buf);
        else if (packet instanceof RemoteRollbackRequestPayload p) p.encode(buf);
        else if (packet instanceof SyncResponsePayload p) p.encode(buf);
        else if (packet instanceof DownloadStructureResultPayload p) p.encode(buf);
        else if (packet instanceof UploadResponsePayload p) p.encode(buf);
        else if (packet instanceof CaptureBlockEntityNbtResponsePayload p) p.encode(buf);
        else if (packet instanceof PermissionListResponsePayload p) p.encode(buf);
        else if (packet instanceof BlueprintConfigResponsePayload p) p.encode(buf);
        else if (packet instanceof ProjectorFeatureConfigResponsePayload p) p.encode(buf);
        else if (packet instanceof FeatureAvailabilityPayload p) p.encode(buf);
        else if (packet instanceof RemoteCatalogResponsePayload p) p.encode(buf);
        else if (packet instanceof RemoteActionResponsePayload p) p.encode(buf);
        else if (packet instanceof RemoteHistoryResponsePayload p) p.encode(buf);
        else throw new IllegalArgumentException("Unknown packet type: " + packet.getClass().getName());
    }
}
