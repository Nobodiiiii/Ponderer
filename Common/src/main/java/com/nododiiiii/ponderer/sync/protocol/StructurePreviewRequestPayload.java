package com.nododiiiii.ponderer.sync.protocol;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;


/** Reads preview resources only; never generates a structure or loads a chunk. */
public record StructurePreviewRequestPayload(long requestId, String structureId) implements CustomPacketPayload {
    public static final int MAX_TEMPLATE_BYTES = 768 * 1024;
    public static final int MAX_STRUCTURES = 16384;
    public static final String STATUS_PREFIX = "ponderer.ui.structure_list.preview.";
    public static final Type<StructurePreviewRequestPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "structure_preview_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StructurePreviewRequestPayload> CODEC =
        StreamCodec.of((buf, payload) -> {
            buf.writeLong(payload.requestId());
            buf.writeUtf(payload.structureId(), 256);
        }, buf -> new StructurePreviewRequestPayload(buf.readLong(), buf.readUtf(256)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}
