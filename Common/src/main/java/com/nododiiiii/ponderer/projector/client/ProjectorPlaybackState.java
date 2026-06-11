package com.nododiiiii.ponderer.projector.client;

import com.nododiiiii.ponderer.projector.ProjectorBlockEntity;
import com.nododiiiii.ponderer.projector.ProjectorSceneResolver;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ProjectorPlaybackState {

    record PreparedFrame(ProjectorSceneBundle bundle, ProjectorSceneBundle.Segment segment,
                         PonderScene activeScene, int globalTick, int localTick) {
    }

    private static final Map<BlockPos, ProjectorPlaybackState> STATES = new HashMap<>();
    @Nullable
    private static Level cachedLevel;

    @Nullable
    private ProjectorSceneBundle bundle;
    private String bundleKey = "";
    private int lastRevision = Integer.MIN_VALUE;
    private int activeSegmentStartTick = -1;
    private int activeLocalTick = -1;

    static ProjectorPlaybackState forBlock(ProjectorBlockEntity blockEntity) {
        Level level = Minecraft.getInstance().level;
        if (level != cachedLevel) {
            STATES.clear();
            cachedLevel = level;
        }
        return STATES.computeIfAbsent(blockEntity.getBlockPos().immutable(), ignored -> new ProjectorPlaybackState());
    }

    @Nullable
    PreparedFrame prepare(ProjectorBlockEntity blockEntity, float partialTick) {
        if (!blockEntity.isPlaying() || !blockEntity.hasRenderableScene()) {
            return null;
        }

        List<String> desiredSceneKeys = blockEntity.getSceneKeys();
        if (desiredSceneKeys.isEmpty()) {
            desiredSceneKeys = ProjectorSceneResolver.sceneKeysFor(blockEntity.getSourceItem());
        }
        if (desiredSceneKeys.isEmpty()) {
            return null;
        }
        String desiredBundleKey = String.join("\n", desiredSceneKeys);

        if (!desiredBundleKey.equals(bundleKey) || bundle == null) {
            bundleKey = desiredBundleKey;
            bundle = ProjectorSceneBundle.compile(desiredSceneKeys);
            lastRevision = Integer.MIN_VALUE;
            activeSegmentStartTick = -1;
            activeLocalTick = -1;
        }

        if (bundle == null || bundle.totalDurationTicks() <= 0 || blockEntity.getLevel() == null) {
            return null;
        }

        long elapsed = Math.max(0L, blockEntity.getLevel().getGameTime() - blockEntity.getPlaybackStartGameTime());
        int totalDuration = bundle.totalDurationTicks();
        int globalTick;
        if (blockEntity.isPlaybackLooping()) {
            globalTick = totalDuration <= 0 ? 0 : (int) (elapsed % totalDuration);
        } else {
            globalTick = (int) Math.min(elapsed, Math.max(0, totalDuration - 1));
        }

        ProjectorSceneBundle.Segment segment = bundle.segmentAt(globalTick);
        if (segment == null) {
            return null;
        }

        int localTick = segment.localTick(globalTick);
        PonderScene activeScene = segment.scene();

        if (blockEntity.getPlaybackRevision() != lastRevision
            || activeSegmentStartTick != segment.startTick()
            || localTick < activeLocalTick) {
            ProjectorRenderContext.run(activeScene::begin);
            activeSegmentStartTick = segment.startTick();
            activeLocalTick = 0;
        }

        if (localTick > activeLocalTick) {
            ProjectorRenderContext.run(() -> activeScene.seekToTime(localTick));
            activeLocalTick = localTick;
        }

        lastRevision = blockEntity.getPlaybackRevision();
        return new PreparedFrame(bundle, segment, activeScene, globalTick, localTick);
    }
}
