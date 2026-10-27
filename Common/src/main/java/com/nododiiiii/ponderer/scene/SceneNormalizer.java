package com.nododiiiii.ponderer.scene;

import java.util.ArrayList;
import java.util.List;

/** Legacy normalization with an explicit host policy for omitted editability. */
public final class SceneNormalizer {
    private SceneNormalizer() {}

    public static void normalize(DslScene scene, boolean defaultEditable) {
        if (scene.editable == null) {
            scene.editable = defaultEditable;
        }
        if (scene.scenes != null) {
            for (DslScene.SceneSegment seg : scene.scenes) {
                ensureFirstStepIsShowStructure(seg);
            }
        }
    }

    private static void ensureFirstStepIsShowStructure(DslScene.SceneSegment seg) {
        if (seg.steps == null || seg.steps.isEmpty()) return;
        for (DslScene.DslStep step : seg.steps) {
            if (step == null || step.type == null) continue;
            if ("show_structure".equalsIgnoreCase(step.type) || "show_interface".equalsIgnoreCase(step.type)) {
                return; // already correct
            }
            break; // first meaningful step is not a valid scene-start step
        }
        // Prepend show_structure + idle(20t)
        List<DslScene.DslStep> fixed = new ArrayList<>();
        DslScene.DslStep showStep = new DslScene.DslStep();
        showStep.type = "show_structure";
        fixed.add(showStep);
        DslScene.DslStep idleStep = new DslScene.DslStep();
        idleStep.type = "idle";
        idleStep.duration = 20;
        fixed.add(idleStep);
        fixed.addAll(seg.steps);
        seg.steps = fixed;
    }
}
