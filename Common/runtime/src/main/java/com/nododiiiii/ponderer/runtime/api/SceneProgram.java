package com.nododiiiii.ponderer.runtime.api;

import com.nododiiiii.ponderer.scene.DslScene;

/** Native Java may freely interleave builder calls, portable actions, DSL steps and registered Java extensions. */
public interface SceneProgram {
    void step(DslScene.DslStep step);
    void beforeStep(String stepId);
    void afterStep(String stepId);
    StepBuildContext context(String stepId);
}
