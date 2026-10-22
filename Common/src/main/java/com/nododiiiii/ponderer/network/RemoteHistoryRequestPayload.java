package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.RemoteWorkspaceService;
import com.nododiiiii.ponderer.ponder.UploadPermissions;
import net.minecraft.network.FriendlyByteBuf;
import com.nododiiiii.ponderer.network.compat.StreamCodec;
import com.nododiiiii.ponderer.network.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record RemoteHistoryRequestPayload(String kind, String id, @Nullable String pack) implements CustomPacketPayload {

    public static final Type<RemoteHistoryRequestPayload> TYPE =
        new Type<>(new ResourceLocation(Ponderer.MODID, "remote_history_request"));
    public static final StreamCodec<FriendlyByteBuf, RemoteHistoryRequestPayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), RemoteHistoryRequestPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(kind());
        buf.writeUtf(id());
        writeOptionalUtf(buf, pack());
    }

    public static RemoteHistoryRequestPayload decode(FriendlyByteBuf buf) {
        return new RemoteHistoryRequestPayload(buf.readUtf(), buf.readUtf(), readOptionalUtf(buf));
    }

    public static void handle(RemoteHistoryRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        UploadPermissions.ensurePullAccess(player);
        List<RemoteWorkspaceService.HistoryEntry> entries = RemoteWorkspaceService.history(
            player.server, payload.kind(), payload.id(), payload.pack());
        PondererServices.NETWORK.sendToPlayer(player,
            RemoteHistoryResponsePayload.fromEntries(payload.kind(), payload.id(), payload.pack(),
                entries, UploadPermissions.canManage(player)));
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
