package com.nododiiiii.ponderer.runtime.api;

/** Builds replayable Ponder instructions; this callback is not a per-frame script. */
public interface StepType<P> {
    StepCodec<P> codec();
    void build(P parameters, StepBuildContext context);
}
