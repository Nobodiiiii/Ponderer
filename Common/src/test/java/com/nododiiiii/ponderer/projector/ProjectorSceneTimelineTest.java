package com.nododiiiii.ponderer.projector;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectorSceneTimelineTest {

    @AfterEach
    void resetScenes() {
        SceneRuntime.setScenes(List.of());
    }

    @Test
    void addsIntermissionsBetweenDslSectionsAndSceneKeysForSingleRun() {
        DslScene first = scene("ponderer:first", segment(10), segment(20));
        DslScene second = scene("ponderer:second", segment(30));
        SceneRuntime.setScenes(List.of(first, second));

        assertEquals(140, ProjectorSceneTimeline.estimatePlaybackTicks(
            List.of("ponderer:first", "ponderer:second"),
            40));
    }

    @Test
    void finalIntermissionIsOnlyIncludedForLoopingTimelines() {
        assertEquals(180, ProjectorSceneTimeline.withIntermissions(100, 3, 40, false));
        assertEquals(220, ProjectorSceneTimeline.withIntermissions(100, 3, 40, true));
    }

    @Test
    void negativeIntermissionBehavesLikeZero() {
        assertEquals(100, ProjectorSceneTimeline.withIntermissions(100, 3, -40, false));
    }

    @Test
    void resolvesPlaybackTicksForLoopingAndPersistedEndings() {
        assertEquals(5, ProjectorSceneTimeline.resolvePlaybackTick(105, 100, true, false, 200));
        assertEquals(ProjectorSceneTimeline.NO_PLAYBACK_TICK,
            ProjectorSceneTimeline.resolvePlaybackTick(105, 100, false, false, 200));
        assertEquals(299, ProjectorSceneTimeline.resolvePlaybackTick(350, 100, false, true, 200));
    }

    @Test
    void normalizesSeekTicksAcrossPlaybackModes() {
        assertEquals(5, ProjectorSceneTimeline.normalizePlaybackSeekTick(105, 100, true, false, 200));
        assertEquals(99, ProjectorSceneTimeline.normalizePlaybackSeekTick(350, 100, false, false, 200));
        assertEquals(299, ProjectorSceneTimeline.normalizePlaybackSeekTick(350, 100, false, true, 200));
    }

    @Test
    void estimatesDslKeyframeTicksAcrossSegmentsAndIntermissions() {
        DslScene scene = scene("ponderer:keyframes",
            segmentWithSteps(
                step("idle", 10, false),
                step("text", 20, true),
                step("idle", 5, false)),
            segmentWithSteps(
                step("idle", 15, true),
                step("show_interface", 30, false)));

        assertEquals(List.of(10, 40), ProjectorSceneTimeline.estimateKeyframeTicks(scene, 5));
    }

    private static DslScene scene(String id, DslScene.SceneSegment... segments) {
        DslScene scene = new DslScene();
        scene.id = id;
        scene.scenes = List.of(segments);
        return scene;
    }

    private static DslScene.SceneSegment segment(int idleTicks) {
        return segmentWithSteps(step("idle", idleTicks, false));
    }

    private static DslScene.SceneSegment segmentWithSteps(DslScene.DslStep... steps) {
        DslScene.SceneSegment segment = new DslScene.SceneSegment();
        segment.steps = List.of(steps);
        return segment;
    }

    private static DslScene.DslStep step(String type, int duration, boolean keyframe) {
        DslScene.DslStep step = new DslScene.DslStep();
        step.type = type;
        step.duration = duration;
        step.attachKeyFrame = keyframe;
        return step;
    }
}
