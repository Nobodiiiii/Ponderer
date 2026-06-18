package com.nododiiiii.ponderer.projector.client;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.PonderInstruction;

public final class ProjectorCueMarkerInstruction extends PonderInstruction {

    private final DslScene.DslStep step;

    public ProjectorCueMarkerInstruction(DslScene.DslStep step) {
        this.step = step;
    }

    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public void tick(PonderScene scene) {
        // no-op
    }

    @Override
    public void onScheduled(PonderScene scene) {
        ProjectorSceneBundle.OverlayCue cue = ProjectorSceneBundle.createOverlayCue(scene.getTotalTime(), step);
        ProjectorCueIndexStore.append(scene, cue);
    }
}
