package com.nododiiiii.ponderer.runtime.api;

import java.util.List;
import net.createmod.ponder.foundation.PonderScene;

/** A fresh scene and its own timeline state; scheduling fills step ticks during begin(). */
public record CompilationResult(PonderScene scene, List<Integer> stepTicks, PlaybackSession playback) {
    public CompilationResult { stepTicks = List.copyOf(stepTicks); }
}
