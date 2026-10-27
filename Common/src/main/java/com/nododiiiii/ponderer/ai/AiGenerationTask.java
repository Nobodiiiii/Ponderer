package com.nododiiiii.ponderer.ai;

import java.util.concurrent.CompletableFuture;

/**
 * Handle for one background AI generation run.
 */
public final class AiGenerationTask {

    private final String runId;
    private final AiCancellationScope cancellation;
    private final CompletableFuture<?> completion;
    private final AiRunControl control;

    public AiGenerationTask(String runId, AiCancellationScope cancellation, CompletableFuture<?> completion, AiRunControl control) {
        this.runId = runId;
        this.cancellation = cancellation;
        this.completion = completion;
        this.control = control;
    }

    public String runId() {
        return runId;
    }

    public boolean cancel() {
        control.close();
        return cancellation.cancel();
    }

    public boolean steer(String instruction) { return !completion.isDone() && control.steer(instruction); }

    public boolean isCancellationRequested() {
        return cancellation.isCancellationRequested();
    }

    public CompletableFuture<?> completion() {
        return completion;
    }
}
