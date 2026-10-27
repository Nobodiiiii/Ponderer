package com.nododiiiii.ponderer.bootstrap;

import com.nododiiiii.ponderer.feedback.WorldFeedback;
import com.nododiiiii.ponderer.editor.capture.EditorWorldTools;

public final class ClientFeatureLifecycle {
    private ClientFeatureLifecycle() {}
    public static void tick() {
        WorldFeedback.beginTick();
        EditorWorldTools.tick();
        WorldFeedback.tick();
    }
}
