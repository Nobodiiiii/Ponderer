package com.nododiiiii.ponderer.projector;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.SceneRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ProjectorSceneTimeline {

    public static final int NO_PLAYBACK_TICK = -1;

    private ProjectorSceneTimeline() {
    }

    public static int estimateTotalTicks(String sceneKey) {
        DslScene scene = SceneRuntime.findByKey(sceneKey);
        if (scene == null || scene.scenes == null || scene.scenes.isEmpty()) {
            return 0;
        }

        int total = 0;
        for (DslScene.SceneSegment segment : scene.scenes) {
            total += estimateSegmentTicks(segment);
        }
        return total;
    }

    public static int estimatePlaybackTicks(List<String> sceneKeys, int intermissionTicks) {
        if (sceneKeys == null || sceneKeys.isEmpty()) {
            return 0;
        }

        int total = 0;
        int segments = 0;
        for (String sceneKey : sceneKeys) {
            int sceneTicks = Math.max(0, estimateTotalTicks(sceneKey));
            int sceneSegments = sceneTicks <= 0 ? 0 : estimateSegmentCount(sceneKey);
            total += sceneTicks;
            segments += sceneSegments;
        }
        return withIntermissions(total, segments, intermissionTicks, false);
    }

    public static int withIntermissions(int activeTicks, int segmentCount, int intermissionTicks,
                                        boolean includeFinalIntermission) {
        int safeActiveTicks = Math.max(0, activeTicks);
        int safeSegmentCount = Math.max(0, segmentCount);
        if (safeActiveTicks <= 0 || safeSegmentCount <= 0) {
            return safeActiveTicks;
        }

        int intervalCount = Math.max(0, safeSegmentCount - 1);
        if (includeFinalIntermission) {
            intervalCount++;
        }
        return safeActiveTicks + Math.max(0, intermissionTicks) * intervalCount;
    }

    public static int resolvePlaybackTick(long elapsedTicks, int totalDurationTicks,
                                          boolean looping, boolean persistAfterPlaybackEnd,
                                          int finalExtraTicks) {
        int safeTotalDuration = Math.max(0, totalDurationTicks);
        long safeElapsed = Math.max(0L, elapsedTicks);
        if (safeTotalDuration <= 0) {
            return looping || persistAfterPlaybackEnd ? 0 : NO_PLAYBACK_TICK;
        }

        if (looping) {
            return safePlaybackTick(safeElapsed % safeTotalDuration);
        }
        if (shouldStopPlayback(safeElapsed, safeTotalDuration, false, persistAfterPlaybackEnd, finalExtraTicks)) {
            return NO_PLAYBACK_TICK;
        }
        if (persistAfterPlaybackEnd) {
            long frozenPlaybackTick = Math.max(0L,
                (long) safeTotalDuration + Math.max(0, finalExtraTicks) - 1L);
            return safePlaybackTick(Math.min(safeElapsed, frozenPlaybackTick));
        }
        return safePlaybackTick(Math.min(safeElapsed, Math.max(0L, safeTotalDuration - 1L)));
    }

    public static boolean shouldStopPlayback(long elapsedTicks, int totalDurationTicks,
                                             boolean looping, boolean persistAfterPlaybackEnd,
                                             int finalExtraTicks) {
        int safeTotalDuration = Math.max(0, totalDurationTicks);
        long safeElapsed = Math.max(0L, elapsedTicks);
        if (looping) {
            return false;
        }
        if (safeTotalDuration <= 0) {
            return !persistAfterPlaybackEnd;
        }
        if (persistAfterPlaybackEnd) {
            return false;
        }
        return safeElapsed >= safeTotalDuration;
    }

    public static boolean isPlaybackFrozen(long elapsedTicks, int totalDurationTicks,
                                           boolean looping, boolean persistAfterPlaybackEnd,
                                           int finalExtraTicks) {
        int safeTotalDuration = Math.max(0, totalDurationTicks);
        long safeElapsed = Math.max(0L, elapsedTicks);
        if (looping || !persistAfterPlaybackEnd || safeTotalDuration <= 0) {
            return false;
        }

        long frozenPlaybackTick = Math.max(0L,
            (long) safeTotalDuration + Math.max(0, finalExtraTicks) - 1L);
        return safeElapsed > frozenPlaybackTick;
    }

    public static int normalizePlaybackSeekTick(int playbackTick, int totalDurationTicks,
                                                boolean looping, boolean persistAfterPlaybackEnd,
                                                int finalExtraTicks) {
        int safeTotalDuration = Math.max(0, totalDurationTicks);
        if (safeTotalDuration <= 0) {
            return 0;
        }

        if (looping) {
            return Math.floorMod(playbackTick, safeTotalDuration);
        }

        int maxPlaybackTick = persistAfterPlaybackEnd
            ? safeTotalDuration + Math.max(0, finalExtraTicks) - 1
            : safeTotalDuration - 1;
        if (maxPlaybackTick <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(playbackTick, maxPlaybackTick));
    }

    public static int estimateSegmentCount(String sceneKey) {
        DslScene scene = SceneRuntime.findByKey(sceneKey);
        if (scene == null || scene.scenes == null || scene.scenes.isEmpty()) {
            return 0;
        }
        return scene.scenes.size();
    }

    public static int estimateSegmentTicks(DslScene.SceneSegment segment) {
        if (segment == null || segment.steps == null) {
            return 0;
        }

        int total = 0;
        for (DslScene.DslStep step : segment.steps) {
            total += estimateStepTicks(step);
        }
        return total;
    }

    public static List<Integer> estimateKeyframeTicks(DslScene scene, int intermissionTicks) {
        if (scene == null || scene.scenes == null || scene.scenes.isEmpty()) {
            return List.of();
        }

        List<Integer> keyframes = new ArrayList<>();
        int timeline = 0;
        int safeIntermission = Math.max(0, intermissionTicks);
        for (int segmentIndex = 0; segmentIndex < scene.scenes.size(); segmentIndex++) {
            DslScene.SceneSegment segment = scene.scenes.get(segmentIndex);
            int localTimeline = 0;
            if (segment != null && segment.steps != null) {
                for (DslScene.DslStep step : segment.steps) {
                    if (Boolean.TRUE.equals(step.attachKeyFrame)) {
                        keyframes.add(timeline + localTimeline);
                    }
                    localTimeline += estimateStepTicks(step);
                }
            }
            timeline += Math.max(1, estimateSegmentTicks(segment));
            if (segmentIndex < scene.scenes.size() - 1) {
                timeline += safeIntermission;
            }
        }
        return List.copyOf(keyframes);
    }

    public static int estimateStepTicks(DslScene.DslStep step) {
        if (step == null || step.type == null) {
            return 0;
        }

        return switch (step.type.toLowerCase(Locale.ROOT)) {
            case "idle" -> step.durationOrDefault(20);
            case "text", "shared_text", "show_controls", "show_interface",
                 "highlight_section" -> step.durationOrDefault(60);
            case "rotate_camera_y", "zoom_scene", "create_entity", "create_item_entity",
                 "hide_section", "show_section", "show_structure", "show_extra_structure",
                 "rotate_section", "move_section", "move_entity", "remove_entities",
                 "move_point_of_interest", "modify_block_entity_nbt", "modify_entities_nbt",
                 "modify_item_entities_nbt", "destroy_block", "indicate_redstone",
                 "indicate_success", "toggle_redstone_power", "replace_blocks", "set_block",
                 "clear_entities", "clear_item_entities", "spawn_particles", "play_sound",
                 "click_interface", "change_interface_slot", "show_section_and_merge" ->
                Math.max(0, step.duration == null ? 0 : step.duration);
            default -> Math.max(0, step.duration == null ? 0 : step.duration);
        };
    }

    private static int safePlaybackTick(long playbackTick) {
        if (playbackTick <= 0L) {
            return 0;
        }
        return playbackTick >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) playbackTick;
    }
}
