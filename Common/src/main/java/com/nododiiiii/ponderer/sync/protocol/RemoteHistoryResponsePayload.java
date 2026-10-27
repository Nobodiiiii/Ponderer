package com.nododiiiii.ponderer.sync.protocol;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record RemoteHistoryResponsePayload(String kind, String id, @Nullable String pack,
                                           List<Entry> entries, boolean canManage) implements CustomPacketPayload {

    public static final Type<RemoteHistoryResponsePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "remote_history_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteHistoryResponsePayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), RemoteHistoryResponsePayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(int revision, String action, String actor, long createdAt, String hash, long size) {
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(kind());
        buf.writeUtf(id());
        writeOptionalUtf(buf, pack());
        buf.writeVarInt(entries().size());
        for (Entry entry : entries()) {
            buf.writeVarInt(entry.revision());
            buf.writeUtf(entry.action() == null ? "" : entry.action());
            buf.writeUtf(entry.actor() == null ? "" : entry.actor());
            buf.writeLong(entry.createdAt());
            buf.writeUtf(entry.hash() == null ? "" : entry.hash());
            buf.writeLong(entry.size());
        }
        buf.writeBoolean(canManage());
    }

    public static RemoteHistoryResponsePayload decode(RegistryFriendlyByteBuf buf) {
        String kind = buf.readUtf();
        String id = buf.readUtf();
        String pack = readOptionalUtf(buf);
        int size = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readLong(), buf.readUtf(), buf.readLong()));
        }
        return new RemoteHistoryResponsePayload(kind, id, pack, List.copyOf(entries), buf.readBoolean());
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
