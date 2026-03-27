package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.ui.NbtPickState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

public record CaptureBlockEntityNbtResponsePayload(BlockPos pos, @Nullable CompoundTag nbt) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos());
        buf.writeBoolean(nbt() != null);
        if (nbt() != null) {
            buf.writeNbt(nbt());
        }
    }

    public static CaptureBlockEntityNbtResponsePayload decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        CompoundTag nbt = buf.readBoolean() ? buf.readNbt() : null;
        return new CaptureBlockEntityNbtResponsePayload(pos, nbt);
    }

    public static void handle(CaptureBlockEntityNbtResponsePayload payload) {
        NbtPickState.handleServerBlockEntityCapture(payload.pos(), payload.nbt());
    }
}
