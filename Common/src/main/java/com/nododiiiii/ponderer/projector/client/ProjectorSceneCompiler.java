package com.nododiiiii.ponderer.projector.client;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.projector.ProjectorSceneTimeline;

import java.util.List;

public final class ProjectorSceneCompiler {

    private ProjectorSceneCompiler() {
    }

    public static int estimateTotalTicks(String sceneKey) {
        ProjectorSceneBundle compiled = ProjectorSceneBundle.compile(sceneKey);
        if (compiled != null && compiled.totalDurationTicks() > 0) {
            return compiled.totalDurationTicks();
        }

        return ProjectorSceneTimeline.estimateTotalTicks(sceneKey);
    }

    public static int estimatePlaybackTicks(List<String> sceneKeys, int intermissionTicks) {
        ProjectorSceneBundle compiled = ProjectorSceneBundle.compile(sceneKeys);
        if (compiled != null && compiled.totalDurationTicks() > 0) {
            return ProjectorSceneTimeline.withIntermissions(
                compiled.totalDurationTicks(),
                compiled.segments().size(),
                intermissionTicks,
                false);
        }

        return ProjectorSceneTimeline.estimatePlaybackTicks(sceneKeys, intermissionTicks);
    }

    public static int estimateSegmentTicks(DslScene.SceneSegment segment) {
        return ProjectorSceneTimeline.estimateSegmentTicks(segment);
    }

    public static int estimateStepTicks(DslScene.DslStep step) {
        return ProjectorSceneTimeline.estimateStepTicks(step);
    }
}
