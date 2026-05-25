package com.nododiiiii.ponderer.ponder;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.PonderInstruction;

/**
 * Non-blocking instruction inserted right before each text / shared_text step.
 * Captures the scene's accumulated totalTime at scheduling time so the side
 * panel can seek to the moment the corresponding text becomes visible.
 */
public final class TextMarkerInstruction extends PonderInstruction {

    private final String preview;
    private final boolean shared;

    public TextMarkerInstruction(String preview, boolean shared) {
        this.preview = preview;
        this.shared = shared;
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
        TextIndexStore.append(scene, scene.getTotalTime(), preview, shared);
    }
}
