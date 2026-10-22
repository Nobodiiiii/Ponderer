package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.projector.ProjectorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import com.nododiiiii.ponderer.network.compat.StreamCodec;
import com.nododiiiii.ponderer.network.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ProjectorPlaybackStartPayload(BlockPos projectorPos, boolean looping) implements CustomPacketPayload {

    public static final Type<ProjectorPlaybackStartPayload> TYPE =
        new Type<>(new ResourceLocation(Ponderer.MODID, "projector_playback_start"));
    public static final StreamCodec<FriendlyByteBuf, ProjectorPlaybackStartPayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), ProjectorPlaybackStartPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(projectorPos);
        buf.writeBoolean(looping);
    }

    public static ProjectorPlaybackStartPayload decode(FriendlyByteBuf buf) {
        return new ProjectorPlaybackStartPayload(buf.readBlockPos(), buf.readBoolean());
    }

    public static void handle(ProjectorPlaybackStartPayload payload) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        if (Minecraft.getInstance().level.getBlockEntity(payload.projectorPos()) instanceof ProjectorBlockEntity projector) {
            projector.startClientPlaybackFromServerSignal(payload.looping());
        }
    }
}
