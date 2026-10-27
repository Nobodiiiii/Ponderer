package com.nododiiiii.ponderer.sync.protocol;

import com.nododiiiii.ponderer.Ponderer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record UploadScenePayload(String sceneId, @Nullable String pack, String json,
                                 List<StructureEntry> structures,
                                 String mode, String lastSyncHash) implements CustomPacketPayload {

    public static final Type<UploadScenePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "upload_scene"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UploadScenePayload> CODEC =
            StreamCodec.of(UploadScenePayload::encode, UploadScenePayload::decode);

    public record StructureEntry(String id, @Nullable String pack, byte[] bytes) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, UploadScenePayload payload) {
        buf.writeUtf(payload.sceneId());
        writeOptionalUtf(buf, payload.pack());
        buf.writeUtf(payload.json());
        buf.writeVarInt(payload.structures().size());
        for (StructureEntry entry : payload.structures()) {
            buf.writeUtf(entry.id());
            writeOptionalUtf(buf, entry.pack());
            buf.writeByteArray(entry.bytes());
        }
        buf.writeUtf(payload.mode() == null ? "check" : payload.mode());
        buf.writeUtf(payload.lastSyncHash() == null ? "" : payload.lastSyncHash());
    }

    private static UploadScenePayload decode(RegistryFriendlyByteBuf buf) {
        String sceneId = buf.readUtf();
        String pack = readOptionalUtf(buf);
        String json = buf.readUtf();
        int size = buf.readVarInt();
        List<StructureEntry> structures = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            structures.add(new StructureEntry(buf.readUtf(), readOptionalUtf(buf), buf.readByteArray()));
        }
        String mode = buf.readUtf();
        String lastSyncHash = buf.readUtf();
        return new UploadScenePayload(sceneId, pack, json, structures, mode, lastSyncHash);
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
