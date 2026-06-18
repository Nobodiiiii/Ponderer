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

public record ProjectorManualTriggerPayload(BlockPos projectorPos) implements CustomPacketPayload {

    public static final Type<ProjectorManualTriggerPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "projector_manual_trigger"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectorManualTriggerPayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), ProjectorManualTriggerPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(projectorPos);
    }

    public static ProjectorManualTriggerPayload decode(RegistryFriendlyByteBuf buf) {
        return new ProjectorManualTriggerPayload(buf.readBlockPos());
    }

    public static void handle(ProjectorManualTriggerPayload payload, @Nullable ServerPlayer player) {
        if (player == null
            || !player.serverLevel().hasChunkAt(payload.projectorPos())
            || player.distanceToSqr(payload.projectorPos().getCenter()) > 64.0D) {
            return;
        }
        if (player.serverLevel().getBlockEntity(payload.projectorPos()) instanceof ProjectorBlockEntity projector) {
            projector.triggerManualOnce();
        }
    }
}
