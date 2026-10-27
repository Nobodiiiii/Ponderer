package com.nododiiiii.ponderer.runtime.core;

import com.nododiiiii.ponderer.scene.DslScene;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.PonderInstruction;
import com.nododiiiii.ponderer.runtime.api.OverlayTimeline;

public final class OverlayMarkerInstruction extends PonderInstruction {
    private final DslScene.DslStep step;
    public OverlayMarkerInstruction(DslScene.DslStep step) { this.step = step; }
    @Override public boolean isComplete() { return true; }
    @Override public void tick(PonderScene scene) {}
    @Override public void onScheduled(PonderScene scene) { OverlayTimeline.append(scene, scene.getTotalTime(), step); }
}
