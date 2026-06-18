package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.ponder.RemoteWorkspaceService;
import com.nododiiiii.ponderer.ui.RemoteBrowserScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record RemoteCatalogResponsePayload(List<Entry> scenes, List<Entry> structures, List<Entry> packs,
                                           boolean canPull, boolean canUpload, boolean canManage,
                                           String message, boolean error) implements CustomPacketPayload {

    public static final Type<RemoteCatalogResponsePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "remote_catalog_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteCatalogResponsePayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), RemoteCatalogResponsePayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public record Entry(String kind, String id, @Nullable String pack, String title, String summary,
                        String hash, int revision, long size, long updatedAt, String updatedBy,
                        int dependencyCount, int refCount) {
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        writeEntries(buf, scenes());
        writeEntries(buf, structures());
        writeEntries(buf, packs());
        buf.writeBoolean(canPull());
        buf.writeBoolean(canUpload());
        buf.writeBoolean(canManage());
        buf.writeUtf(message() == null ? "" : message());
        buf.writeBoolean(error());
    }

    public static RemoteCatalogResponsePayload decode(RegistryFriendlyByteBuf buf) {
        return new RemoteCatalogResponsePayload(
            readEntries(buf),
            readEntries(buf),
            readEntries(buf),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readUtf(),
            buf.readBoolean());
    }

    public static RemoteCatalogResponsePayload fromSnapshot(RemoteWorkspaceService.CatalogSnapshot snapshot,
                                                            String message, boolean error) {
        return new RemoteCatalogResponsePayload(
            snapshot.scenes().stream().map(RemoteCatalogResponsePayload::fromServiceEntry).toList(),
            snapshot.structures().stream().map(RemoteCatalogResponsePayload::fromServiceEntry).toList(),
            snapshot.packs().stream().map(RemoteCatalogResponsePayload::fromServiceEntry).toList(),
            snapshot.canPull(),
            snapshot.canUpload(),
            snapshot.canManage(),
            message == null ? "" : message,
            error);
    }

    public static void handle(RemoteCatalogResponsePayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof RemoteBrowserScreen screen) {
            screen.receiveCatalog(payload);
        }
    }

    private static Entry fromServiceEntry(RemoteWorkspaceService.CatalogEntry entry) {
        return new Entry(entry.kind(), entry.id(), entry.pack(), entry.title(), entry.summary(), entry.hash(),
            entry.revision(), entry.size(), entry.updatedAt(), entry.updatedBy(), entry.dependencyCount(), entry.refCount());
    }

    private static void writeEntries(RegistryFriendlyByteBuf buf, List<Entry> entries) {
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeUtf(entry.kind());
            buf.writeUtf(entry.id());
            writeOptionalUtf(buf, entry.pack());
            buf.writeUtf(entry.title() == null ? "" : entry.title());
            buf.writeUtf(entry.summary() == null ? "" : entry.summary());
            buf.writeUtf(entry.hash() == null ? "" : entry.hash());
            buf.writeVarInt(entry.revision());
            buf.writeLong(entry.size());
            buf.writeLong(entry.updatedAt());
            buf.writeUtf(entry.updatedBy() == null ? "" : entry.updatedBy());
            buf.writeVarInt(entry.dependencyCount());
            buf.writeVarInt(entry.refCount());
        }
    }

    private static List<Entry> readEntries(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(
                buf.readUtf(),
                buf.readUtf(),
                readOptionalUtf(buf),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readLong(),
                buf.readLong(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readVarInt()));
        }
        return List.copyOf(entries);
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
