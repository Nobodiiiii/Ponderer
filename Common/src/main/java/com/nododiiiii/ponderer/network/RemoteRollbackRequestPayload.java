package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.RemoteWorkspaceService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public record RemoteRollbackRequestPayload(String kind, String id, @Nullable String pack, int revision) implements CustomPacketPayload {

    public static final Type<RemoteRollbackRequestPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "remote_rollback_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteRollbackRequestPayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), RemoteRollbackRequestPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(kind());
        buf.writeUtf(id());
        writeOptionalUtf(buf, pack());
        buf.writeVarInt(revision());
    }

    public static RemoteRollbackRequestPayload decode(RegistryFriendlyByteBuf buf) {
        return new RemoteRollbackRequestPayload(buf.readUtf(), buf.readUtf(), readOptionalUtf(buf), buf.readVarInt());
    }

    public static void handle(RemoteRollbackRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        RemoteWorkspaceService.OperationResult result = RemoteWorkspaceService.rollback(
            player.server, player, payload.kind(), payload.id(), payload.pack(), payload.revision());
        PondererServices.NETWORK.sendToPlayer(player,
            new RemoteActionResponsePayload(result.success(), result.message(), result.refreshCatalog()));
    }

    private static void writeOptionalUtf(RegistryFriendlyByteBuf buf, @Nullable String value) {
        boolean present = value != null && !value.isBlank();
        buf.writeBoolean(present);
        if (present) {
            buf.writeUtf(value);
        }
    }

    @Nullable
    private static String readOptionalUtf(RegistryFriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUtf() : null;
    }
}
