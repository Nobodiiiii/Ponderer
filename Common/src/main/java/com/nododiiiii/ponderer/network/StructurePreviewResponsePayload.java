package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.editor.screen.StructureListScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record StructurePreviewResponsePayload(long requestId, String structureId, List<String> structures,
                                               byte[] template, String statusKey) implements CustomPacketPayload {
    public static final Type<StructurePreviewResponsePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "structure_preview_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StructurePreviewResponsePayload> CODEC =
        StreamCodec.of(StructurePreviewResponsePayload::encode, StructurePreviewResponsePayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, StructurePreviewResponsePayload payload) {
        buf.writeLong(payload.requestId());
        buf.writeUtf(payload.structureId(), 256);
        buf.writeVarInt(payload.structures().size());
        for (String id : payload.structures()) buf.writeUtf(id, 256);
        buf.writeByteArray(payload.template());
        buf.writeUtf(payload.statusKey(), 256);
    }

    private static StructurePreviewResponsePayload decode(RegistryFriendlyByteBuf buf) {
        long requestId = buf.readLong();
        String structureId = buf.readUtf(256);
        int count = buf.readVarInt();
        if (count < 0 || count > StructurePreviewRequestPayload.MAX_STRUCTURES) {
            throw new IllegalArgumentException("Invalid structure catalog size");
        }
        List<String> structures = new ArrayList<>(count);
        for (int i = 0; i < count; i++) structures.add(buf.readUtf(256));
        return new StructurePreviewResponsePayload(requestId, structureId, List.copyOf(structures),
            buf.readByteArray(StructurePreviewRequestPayload.MAX_TEMPLATE_BYTES), buf.readUtf(256));
    }

    public static void handle(StructurePreviewResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof StructureListScreen screen) {
            screen.acceptPreviewResponse(payload);
        }
    }
}
