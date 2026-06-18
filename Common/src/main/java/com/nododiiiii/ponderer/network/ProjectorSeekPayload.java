package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.projector.ProjectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public record ProjectorSeekPayload(BlockPos projectorPos, int playbackTick) implements CustomPacketPayload {

    public static final Type<ProjectorSeekPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "projector_seek"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectorSeekPayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), ProjectorSeekPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(projectorPos);
        buf.writeVarInt(Math.max(0, playbackTick));
    }

    public static ProjectorSeekPayload decode(RegistryFriendlyByteBuf buf) {
        return new ProjectorSeekPayload(buf.readBlockPos(), buf.readVarInt());
    }

    public static void handle(ProjectorSeekPayload payload, @Nullable ServerPlayer player) {
        if (player == null
            || !player.serverLevel().hasChunkAt(payload.projectorPos())
            || player.distanceToSqr(payload.projectorPos().getCenter()) > 64.0D) {
            return;
        }
        if (player.serverLevel().getBlockEntity(payload.projectorPos()) instanceof ProjectorBlockEntity projector) {
            projector.seekPlaybackToTick(payload.playbackTick());
        }
    }
}
