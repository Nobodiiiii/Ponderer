package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.RemoteWorkspaceService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public record RemoteRollbackRequestPayload(String kind, String id, @Nullable String pack, int revision) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(kind());
        buf.writeUtf(id());
        writeOptionalUtf(buf, pack());
        buf.writeVarInt(revision());
    }

    public static RemoteRollbackRequestPayload decode(FriendlyByteBuf buf) {
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

    private static void writeOptionalUtf(FriendlyByteBuf buf, @Nullable String value) {
        boolean present = value != null && !value.isBlank();
        buf.writeBoolean(present);
        if (present) {
            buf.writeUtf(value);
        }
    }

    @Nullable
    private static String readOptionalUtf(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUtf() : null;
    }
}
