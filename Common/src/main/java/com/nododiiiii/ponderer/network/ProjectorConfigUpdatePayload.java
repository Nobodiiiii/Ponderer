package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.projector.ProjectorBlockEntity;
import com.nododiiiii.ponderer.projector.ProjectorProjectionMode;
import com.nododiiiii.ponderer.projector.ProjectorTriggerMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record ProjectorConfigUpdatePayload(BlockPos projectorPos, List<String> sceneKeys,
                                           ProjectorTriggerMode triggerMode, @Nullable BlockPos anchorPos,
                                           int playbackDurationTicks, int intermissionTicks,
                                           boolean showBlueTint, boolean overlayAntiOcclusion,
                                           boolean compatibilityMode, ProjectorProjectionMode projectionMode,
                                           float miniatureScale, float textScale) {

    private static final int MAX_SCENE_KEYS = 256;
    private static final int MAX_SCENE_KEY_LENGTH = 1024;
    private static final int MAX_TRIGGER_MODE_LENGTH = 64;
    private static final int MAX_PROJECTION_MODE_LENGTH = 64;

    public ProjectorConfigUpdatePayload {
        sceneKeys = sceneKeys == null ? List.of() : List.copyOf(sceneKeys);
        projectionMode = projectionMode == null ? ProjectorProjectionMode.DEFAULT : projectionMode;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(projectorPos);
        buf.writeVarInt(Math.min(sceneKeys.size(), MAX_SCENE_KEYS));
        for (int i = 0; i < sceneKeys.size() && i < MAX_SCENE_KEYS; i++) {
            buf.writeUtf(sceneKeys.get(i), MAX_SCENE_KEY_LENGTH);
        }
        buf.writeUtf(triggerMode.serializedName(), MAX_TRIGGER_MODE_LENGTH);
        buf.writeBoolean(anchorPos != null);
        if (anchorPos != null) {
            buf.writeBlockPos(anchorPos);
        }
        buf.writeVarInt(playbackDurationTicks);
        buf.writeVarInt(intermissionTicks);
        buf.writeBoolean(showBlueTint);
        buf.writeBoolean(overlayAntiOcclusion);
        buf.writeBoolean(compatibilityMode);
        buf.writeUtf(projectionMode.serializedName(), MAX_PROJECTION_MODE_LENGTH);
        buf.writeFloat(miniatureScale);
        buf.writeFloat(textScale);
    }

    public static ProjectorConfigUpdatePayload decode(FriendlyByteBuf buf) {
        BlockPos projectorPos = buf.readBlockPos();
        int keyCount = buf.readVarInt();
        if (keyCount < 0 || keyCount > MAX_SCENE_KEYS) {
            throw new IllegalArgumentException("Invalid projector scene key count: " + keyCount);
        }

        List<String> sceneKeys = new ArrayList<>(keyCount);
        for (int i = 0; i < keyCount; i++) {
            sceneKeys.add(buf.readUtf(MAX_SCENE_KEY_LENGTH));
        }
        ProjectorTriggerMode triggerMode = ProjectorTriggerMode.byName(buf.readUtf(MAX_TRIGGER_MODE_LENGTH));
        BlockPos anchorPos = buf.readBoolean() ? buf.readBlockPos() : null;
        int playbackDurationTicks = buf.readVarInt();
        int intermissionTicks = buf.readVarInt();
        boolean showBlueTint = buf.readBoolean();
        boolean overlayAntiOcclusion = buf.readBoolean();
        boolean compatibilityMode = buf.readBoolean();
        ProjectorProjectionMode projectionMode = ProjectorProjectionMode.byName(buf.readUtf(MAX_PROJECTION_MODE_LENGTH));
        float miniatureScale = buf.readFloat();
        float textScale = buf.readFloat();
        return new ProjectorConfigUpdatePayload(projectorPos, sceneKeys, triggerMode, anchorPos,
            playbackDurationTicks, intermissionTicks, showBlueTint, overlayAntiOcclusion,
            compatibilityMode, projectionMode, miniatureScale, textScale);
    }

    public static void handle(ProjectorConfigUpdatePayload payload, @Nullable ServerPlayer player) {
        if (player == null || !isAuthorized(player, payload.projectorPos())) {
            return;
        }
        if (!(player.serverLevel().getBlockEntity(payload.projectorPos()) instanceof ProjectorBlockEntity projector)) {
            return;
        }
        projector.applyConfig(payload.sceneKeys(), payload.triggerMode(), payload.anchorPos(),
            payload.playbackDurationTicks(), payload.intermissionTicks(), payload.showBlueTint(),
            payload.overlayAntiOcclusion(), payload.compatibilityMode(), payload.projectionMode(),
            payload.miniatureScale(), payload.textScale());
    }

    private static boolean isAuthorized(ServerPlayer player, BlockPos pos) {
        return player.serverLevel().hasChunkAt(pos) && player.distanceToSqr(pos.getCenter()) <= 64.0D;
    }
}
