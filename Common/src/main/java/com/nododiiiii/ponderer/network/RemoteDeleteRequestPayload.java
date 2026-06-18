package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.RemoteWorkspaceService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public record RemoteDeleteRequestPayload(String kind, String id, @Nullable String pack) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(kind());
        buf.writeUtf(id());
        writeOptionalUtf(buf, pack());
    }

    public static RemoteDeleteRequestPayload decode(FriendlyByteBuf buf) {
        return new RemoteDeleteRequestPayload(buf.readUtf(), buf.readUtf(), readOptionalUtf(buf));
    }

    public static void handle(RemoteDeleteRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        RemoteWorkspaceService.OperationResult result = RemoteWorkspaceService.delete(
            player.server, player, payload.kind(), payload.id(), payload.pack());
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
