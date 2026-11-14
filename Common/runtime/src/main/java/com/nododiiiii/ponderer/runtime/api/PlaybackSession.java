package com.nododiiiii.ponderer.runtime.api;

/** Per-compilation timeline control. No editor, product directory or global draft ownership. */
public interface PlaybackSession extends AutoCloseable {
    void previewStep(int index);
    boolean isPreviewingStep();
    boolean isStepComplete();
    void resumeTimeline();
    @Override default void close() { resumeTimeline(); }
}
