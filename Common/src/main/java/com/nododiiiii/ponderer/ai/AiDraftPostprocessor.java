package com.nododiiiii.ponderer.ai;

import com.nododiiiii.ponderer.scene.DslScene;
import java.util.List;

final class AiDraftPostprocessor {
    private AiDraftPostprocessor() {}
    static void placeTextNearTarget(DslScene scene) {
        if (scene.scenes == null) return;
        for (DslScene.SceneSegment segment : scene.scenes) {
            if (segment == null || segment.steps == null) continue;
            for (DslScene.DslStep step : segment.steps) {
                if (step == null || step.type == null) continue;
                String type = step.type.trim().toLowerCase(java.util.Locale.ROOT);
                if ("text".equals(type) || "shared_text".equals(type)) step.placeNearTarget = true;
            }
        }
    }

    static void autoAddKeyFrames(DslScene scene) {
        if (scene.scenes != null) {
            for (DslScene.SceneSegment seg : scene.scenes) {
                if (seg.steps != null) addKeyFramesToSteps(seg.steps);
            }
        }
    }

    private static void addKeyFramesToSteps(List<DslScene.DslStep> steps) {
        boolean needKeyFrame = true; // start true so first action step gets a keyframe
        for (DslScene.DslStep step : steps) {
            if (step.type == null) continue;
            String t = step.type.toLowerCase(java.util.Locale.ROOT);
            if ("text".equals(t) || "shared_text".equals(t) || "idle".equals(t)) {
                needKeyFrame = true;
            } else {
                if (needKeyFrame) {
                    step.attachKeyFrame = true;
                    needKeyFrame = false;
                }
            }
        }
    }
}
