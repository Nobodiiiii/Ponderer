package com.nododiiiii.ponderer.sync.protocol;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record SyncResponsePayload(List<FileEntry> scripts, List<FileEntry> structures,
                                  List<DeleteEntry> deletedScripts, List<DeleteEntry> deletedStructures,
                                  boolean finalChunk, int serverSkippedCount,
                                  String mode, String reason) implements CustomPacketPayload {

    public static final Type<SyncResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "sync_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncResponsePayload> CODEC =
            StreamCodec.of((buf, payload) -> payload.encode(buf), SyncResponsePayload::decode);


    public record FileEntry(String id, @Nullable String pack, byte[] bytes) {
    }

    public record DeleteEntry(String id, @Nullable String pack) {
    }

    public SyncResponsePayload(List<FileEntry> scripts, List<FileEntry> structures, boolean finalChunk,
                               int serverSkippedCount) {
        this(scripts, structures, List.of(), List.of(), finalChunk, serverSkippedCount, "", "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(scripts().size());
        for (FileEntry entry : scripts()) {
            writeEntry(buf, entry);
        }
        buf.writeVarInt(structures().size());
        for (FileEntry entry : structures()) {
            writeEntry(buf, entry);
        }
        buf.writeVarInt(deletedScripts().size());
        for (DeleteEntry entry : deletedScripts()) {
            writeDeleteEntry(buf, entry);
        }
        buf.writeVarInt(deletedStructures().size());
        for (DeleteEntry entry : deletedStructures()) {
            writeDeleteEntry(buf, entry);
        }
        buf.writeBoolean(finalChunk());
        buf.writeVarInt(serverSkippedCount());
        buf.writeUtf(mode() == null ? "" : mode());
        buf.writeUtf(reason() == null ? "" : reason());
    }

    public static SyncResponsePayload decode(RegistryFriendlyByteBuf buf) {
        int scriptsSize = buf.readVarInt();
        List<FileEntry> scripts = new ArrayList<>(scriptsSize);
        for (int i = 0; i < scriptsSize; i++) {
            scripts.add(readEntry(buf));
        }
        int structuresSize = buf.readVarInt();
        List<FileEntry> structures = new ArrayList<>(structuresSize);
        for (int i = 0; i < structuresSize; i++) {
            structures.add(readEntry(buf));
        }
        int deletedScriptsSize = buf.readVarInt();
        List<DeleteEntry> deletedScripts = new ArrayList<>(deletedScriptsSize);
        for (int i = 0; i < deletedScriptsSize; i++) {
            deletedScripts.add(readDeleteEntry(buf));
        }
        int deletedStructuresSize = buf.readVarInt();
        List<DeleteEntry> deletedStructures = new ArrayList<>(deletedStructuresSize);
        for (int i = 0; i < deletedStructuresSize; i++) {
            deletedStructures.add(readDeleteEntry(buf));
        }
        boolean finalChunk = buf.readBoolean();
        int serverSkippedCount = buf.readVarInt();
        String mode = buf.readUtf();
        String reason = buf.readUtf();
        return new SyncResponsePayload(scripts, structures, deletedScripts, deletedStructures, finalChunk,
            serverSkippedCount, mode, reason);
    }

    /** Apply only the remote versions the user explicitly selected in remote management. */

    private static void writeEntry(RegistryFriendlyByteBuf buf, FileEntry entry) {
        buf.writeUtf(entry.id());
        writeOptionalUtf(buf, entry.pack());
        buf.writeByteArray(entry.bytes());
    }

    private static void writeDeleteEntry(RegistryFriendlyByteBuf buf, DeleteEntry entry) {
        buf.writeUtf(entry.id());
        writeOptionalUtf(buf, entry.pack());
    }

    private static FileEntry readEntry(RegistryFriendlyByteBuf buf) {
        return new FileEntry(buf.readUtf(), readOptionalUtf(buf), buf.readByteArray());
    }

    private static DeleteEntry readDeleteEntry(RegistryFriendlyByteBuf buf) {
        return new DeleteEntry(buf.readUtf(), readOptionalUtf(buf));
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
