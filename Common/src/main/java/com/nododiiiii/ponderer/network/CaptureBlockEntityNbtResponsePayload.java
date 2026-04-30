package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.ui.NbtPickState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record CaptureBlockEntityNbtResponsePayload(BlockPos pos, @Nullable CompoundTag nbt) implements CustomPacketPayload {

    public static final Type<CaptureBlockEntityNbtResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "capture_block_entity_nbt_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CaptureBlockEntityNbtResponsePayload> CODEC =
            StreamCodec.of(CaptureBlockEntityNbtResponsePayload::encode, CaptureBlockEntityNbtResponsePayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, CaptureBlockEntityNbtResponsePayload payload) {
        buf.writeBlockPos(payload.pos());
        buf.writeBoolean(payload.nbt() != null);
        if (payload.nbt() != null) {
            buf.writeNbt(payload.nbt());
        }
    }

    private static CaptureBlockEntityNbtResponsePayload decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        CompoundTag nbt = buf.readBoolean() ? buf.readNbt() : null;
        return new CaptureBlockEntityNbtResponsePayload(pos, nbt);
    }

    public static void handle(CaptureBlockEntityNbtResponsePayload payload) {
        NbtPickState.handleServerBlockEntityCapture(payload.pos(), payload.nbt());
    }
}
