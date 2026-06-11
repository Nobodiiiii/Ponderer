package com.nododiiiii.ponderer.projector.client;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.projector.ProjectorSceneTimeline;

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

    public static int estimateSegmentTicks(DslScene.SceneSegment segment) {
        return ProjectorSceneTimeline.estimateSegmentTicks(segment);
    }

    public static int estimateStepTicks(DslScene.DslStep step) {
        return ProjectorSceneTimeline.estimateStepTicks(step);
    }
}
