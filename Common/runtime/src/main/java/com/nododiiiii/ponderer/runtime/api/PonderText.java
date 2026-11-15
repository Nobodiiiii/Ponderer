package com.nododiiiii.ponderer.runtime.api;

import com.nododiiiii.ponderer.runtime.core.TextMarkerInstruction;
import net.createmod.ponder.api.scene.SceneBuilder;

/** Native text and JSON steps feed the same replay-safe player text index. */
public final class PonderText {
    private PonderText() {}

    public static void mark(SceneBuilder scene, String text) {
        if (RuntimeFeatures.enabled(RuntimeFeature.TEXT))
            scene.addInstruction(new TextMarkerInstruction(text, false));
    }
}
