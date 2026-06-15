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

    private static DslScene scene(String id, DslScene.SceneSegment... segments) {
        DslScene scene = new DslScene();
        scene.id = id;
        scene.scenes = List.of(segments);
        return scene;
    }

    private static DslScene.SceneSegment segment(int idleTicks) {
        DslScene.DslStep step = new DslScene.DslStep();
        step.type = "idle";
        step.duration = idleTicks;

        DslScene.SceneSegment segment = new DslScene.SceneSegment();
        segment.steps = List.of(step);
        return segment;
    }
}
