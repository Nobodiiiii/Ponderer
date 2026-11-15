package com.nododiiiii.ponderer.runtime.api;

import com.nododiiiii.ponderer.scene.DslScene;

/** Native Java may freely interleave builder calls, portable actions, DSL steps and registered Java extensions. */
public interface SceneProgram extends AutoCloseable {
    void step(DslScene.DslStep step);
    StepBuildContext context();
    @Override void close();
}
