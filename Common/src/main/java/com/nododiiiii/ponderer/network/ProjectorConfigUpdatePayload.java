package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.projector.ProjectorBlockEntity;
import com.nododiiiii.ponderer.projector.ProjectorTriggerMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record ProjectorConfigUpdatePayload(BlockPos projectorPos, List<String> sceneKeys,
                                           ProjectorTriggerMode triggerMode, @Nullable BlockPos anchorPos,
                                           int playbackDurationTicks, boolean showBlueTint) {

    private static final int MAX_SCENE_KEYS = 256;

    public ProjectorConfigUpdatePayload {
        sceneKeys = sceneKeys == null ? List.of() : List.copyOf(sceneKeys);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(projectorPos);
        buf.writeVarInt(Math.min(sceneKeys.size(), MAX_SCENE_KEYS));
        for (int i = 0; i < sceneKeys.size() && i < MAX_SCENE_KEYS; i++) {
            buf.writeUtf(sceneKeys.get(i));
        }
        buf.writeUtf(triggerMode.serializedName());
        buf.writeBoolean(anchorPos != null);
        if (anchorPos != null) {
            buf.writeBlockPos(anchorPos);
        }
        buf.writeVarInt(playbackDurationTicks);
        buf.writeBoolean(showBlueTint);
    }

    public static ProjectorConfigUpdatePayload decode(FriendlyByteBuf buf) {
        BlockPos projectorPos = buf.readBlockPos();
        int keyCount = buf.readVarInt();
        List<String> sceneKeys = new ArrayList<>(Math.min(keyCount, MAX_SCENE_KEYS));
        for (int i = 0; i < keyCount; i++) {
            String key = buf.readUtf();
            if (i < MAX_SCENE_KEYS) {
                sceneKeys.add(key);
            }
        }
        ProjectorTriggerMode triggerMode = ProjectorTriggerMode.byName(buf.readUtf());
        BlockPos anchorPos = buf.readBoolean() ? buf.readBlockPos() : null;
        int playbackDurationTicks = buf.readVarInt();
        boolean showBlueTint = buf.readBoolean();
        return new ProjectorConfigUpdatePayload(projectorPos, sceneKeys, triggerMode, anchorPos, playbackDurationTicks,
            showBlueTint);
    }

    public static void handle(ProjectorConfigUpdatePayload payload, @Nullable ServerPlayer player) {
        if (player == null || !isAuthorized(player, payload.projectorPos())) {
            return;
        }
        if (!(player.serverLevel().getBlockEntity(payload.projectorPos()) instanceof ProjectorBlockEntity projector)) {
            return;
        }
        projector.applyConfig(payload.sceneKeys(), payload.triggerMode(), payload.anchorPos(),
            payload.playbackDurationTicks(), payload.showBlueTint());
    }

    private static boolean isAuthorized(ServerPlayer player, BlockPos pos) {
        return player.serverLevel().hasChunkAt(pos) && player.distanceToSqr(pos.getCenter()) <= 64.0D;
    }
}
