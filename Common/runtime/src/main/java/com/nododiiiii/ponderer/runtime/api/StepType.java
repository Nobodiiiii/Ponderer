package com.nododiiiii.ponderer.runtime.api;

/** Builds replayable Ponder instructions; this callback is not a per-frame script. */
public interface StepType<P> {
    StepCodec<P> codec();
    /** False for opaque compiled Java: preserve the step, but do not offer parameter parsing. */
    default boolean editableParameters() { return true; }
    default String editorLabel() { return ""; }
    void build(P parameters, StepBuildContext context);
}
