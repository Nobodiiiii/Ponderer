package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.projector.ProjectorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public record ProjectorPlaybackStartPayload(BlockPos projectorPos, boolean looping) {

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
