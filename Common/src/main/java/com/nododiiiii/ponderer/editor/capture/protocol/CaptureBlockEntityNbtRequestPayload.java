package com.nododiiiii.ponderer.editor.capture.protocol;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CaptureBlockEntityNbtRequestPayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<CaptureBlockEntityNbtRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "capture_block_entity_nbt_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CaptureBlockEntityNbtRequestPayload> CODEC =
            StreamCodec.of(CaptureBlockEntityNbtRequestPayload::encode, CaptureBlockEntityNbtRequestPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, CaptureBlockEntityNbtRequestPayload payload) {
        buf.writeBlockPos(payload.pos());
    }

    private static CaptureBlockEntityNbtRequestPayload decode(RegistryFriendlyByteBuf buf) {
        return new CaptureBlockEntityNbtRequestPayload(buf.readBlockPos());
    }

}
