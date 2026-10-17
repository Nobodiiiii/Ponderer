package com.nododiiiii.ponderer.ai;

import java.util.concurrent.CompletableFuture;

/**
 * Handle for one background AI generation run.
 */
public final class AiGenerationTask {

    private final String runId;
    private final AiCancellationScope cancellation;
    private final CompletableFuture<?> completion;

    AiGenerationTask(String runId, AiCancellationScope cancellation, CompletableFuture<?> completion) {
        this.runId = runId;
        this.cancellation = cancellation;
        this.completion = completion;
    }

    public String runId() {
        return runId;
    }

    public boolean cancel() {
        return cancellation.cancel();
    }

    public boolean isCancellationRequested() {
        return cancellation.isCancellationRequested();
    }

    public CompletableFuture<?> completion() {
        return completion;
    }
}
