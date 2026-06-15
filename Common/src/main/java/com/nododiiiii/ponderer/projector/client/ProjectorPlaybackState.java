package com.nododiiiii.ponderer.projector.client;

import com.nododiiiii.ponderer.projector.ProjectorBlockEntity;
import com.nododiiiii.ponderer.projector.ProjectorSceneTimeline;
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

    static void clearAll() {
        STATES.clear();
        cachedLevel = null;
    }

    static void clear(BlockPos pos) {
        if (pos != null) {
            STATES.remove(pos.immutable());
        }
    }

    @Nullable
    PreparedFrame prepare(ProjectorBlockEntity blockEntity, float partialTick) {
        if (!blockEntity.isPlaying() || !blockEntity.hasRenderableScene()) {
            return null;
        }

        List<String> desiredSceneKeys = blockEntity.getSceneKeys();
        if (desiredSceneKeys.isEmpty()) {
            desiredSceneKeys = ProjectorClientSceneResolver.sceneKeysFor(blockEntity.getSourceItem());
        }
        ProjectorSceneBundle preparedBundle = bundleFor(desiredSceneKeys);

        if (preparedBundle == null || preparedBundle.totalDurationTicks() <= 0 || blockEntity.getLevel() == null) {
            return null;
        }

        long elapsed = Math.max(0L, blockEntity.getLevel().getGameTime() - blockEntity.getPlaybackStartGameTime());
        int totalDuration = ProjectorSceneTimeline.withIntermissions(
            preparedBundle.totalDurationTicks(),
            preparedBundle.segments().size(),
            blockEntity.getIntermissionTicks(),
            blockEntity.isPlaybackLooping());
        int playbackTick;
        if (blockEntity.isPlaybackLooping()) {
            playbackTick = totalDuration <= 0 ? 0 : (int) (elapsed % totalDuration);
        } else {
            playbackTick = (int) Math.min(elapsed, Math.max(0, totalDuration - 1));
        }

        PlaybackCursor cursor = cursorAt(preparedBundle, playbackTick,
            blockEntity.getIntermissionTicks(), blockEntity.isPlaybackLooping());
        if (cursor == null) {
            return null;
        }

        ProjectorSceneBundle.Segment segment = cursor.segment();
        int localTick = cursor.localTick();
        PonderScene activeScene = segment.scene();

        if (blockEntity.getPlaybackRevision() != lastRevision
            || activeSegmentStartTick != segment.startTick()
            || localTick < activeLocalTick) {
            ProjectorRenderContext.run(activeScene::begin);
            activeSegmentStartTick = segment.startTick();
            activeLocalTick = 0;
        }

        if (localTick > activeLocalTick) {
            advanceScene(activeScene, localTick, segment.durationTicks());
        }

        lastRevision = blockEntity.getPlaybackRevision();
        return new PreparedFrame(preparedBundle, segment, activeScene, playbackTick, localTick);
    }

    @Nullable
    private static PlaybackCursor cursorAt(ProjectorSceneBundle bundle, int playbackTick, int intermissionTicks,
                                           boolean includeFinalIntermission) {
        List<ProjectorSceneBundle.Segment> segments = bundle.segments();
        if (segments.isEmpty()) {
            return null;
        }

        int timeline = 0;
        int safeIntermission = Math.max(0, intermissionTicks);
        for (int i = 0; i < segments.size(); i++) {
            ProjectorSceneBundle.Segment segment = segments.get(i);
            int duration = Math.max(1, segment.durationTicks());
            int activeEnd = timeline + duration;
            if (playbackTick < activeEnd) {
                return new PlaybackCursor(segment, Math.max(0, playbackTick - timeline));
            }

            boolean hasFollowingSegment = i < segments.size() - 1;
            int holdEnd = activeEnd + (hasFollowingSegment || includeFinalIntermission ? safeIntermission : 0);
            if (playbackTick < holdEnd) {
                return new PlaybackCursor(segment, duration + Math.max(0, playbackTick - activeEnd));
            }
            timeline = holdEnd;
        }

        ProjectorSceneBundle.Segment last = segments.get(segments.size() - 1);
        return new PlaybackCursor(last, Math.max(0, last.durationTicks() - 1));
    }

    private void advanceScene(PonderScene activeScene, int targetLocalTick, int segmentDuration) {
        int previousLocalTick = activeLocalTick;
        ProjectorRenderContext.run(() -> {
            int activeDuration = Math.max(0, segmentDuration);
            if (previousLocalTick < activeDuration) {
                activeScene.seekToTime(Math.min(targetLocalTick, activeDuration));
            }

            int extraTicks = targetLocalTick - Math.max(previousLocalTick, activeDuration);
            for (int i = 0; i < extraTicks; i++) {
                activeScene.tick();
            }
        });
        activeLocalTick = targetLocalTick;
    }

    @Nullable
    ProjectorSceneBundle bundleFor(List<String> desiredSceneKeys) {
        if (desiredSceneKeys == null || desiredSceneKeys.isEmpty()) {
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
        return bundle;
    }

    private record PlaybackCursor(ProjectorSceneBundle.Segment segment, int localTick) {
    }
}
