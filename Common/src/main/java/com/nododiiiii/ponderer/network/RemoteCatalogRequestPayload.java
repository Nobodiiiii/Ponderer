package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.RemoteWorkspaceService;
import com.nododiiiii.ponderer.ponder.UploadPermissions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public record RemoteCatalogRequestPayload() implements CustomPacketPayload {

    public static final Type<RemoteCatalogRequestPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "remote_catalog_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteCatalogRequestPayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), RemoteCatalogRequestPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
    }

    public static RemoteCatalogRequestPayload decode(RegistryFriendlyByteBuf buf) {
        return new RemoteCatalogRequestPayload();
    }

    public static void handle(RemoteCatalogRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        UploadPermissions.ensurePullAccess(player);
        RemoteWorkspaceService.CatalogSnapshot snapshot = RemoteWorkspaceService.catalog(player.server, player);
        PondererServices.NETWORK.sendToPlayer(player, RemoteCatalogResponsePayload.fromSnapshot(snapshot, "", false));
    }
}
