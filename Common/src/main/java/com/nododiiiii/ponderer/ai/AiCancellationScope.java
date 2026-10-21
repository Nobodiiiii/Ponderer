package com.nododiiiii.ponderer.ai;

import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative cancellation shared by one AI generation run.
 */
public final class AiCancellationScope {

    private static final java.util.concurrent.ScheduledExecutorService TIMER = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ponderer-ai-deadline"); thread.setDaemon(true); return thread;
    });
    private volatile boolean expired;
    private java.util.concurrent.ScheduledFuture<?> deadline;

    public void expireAfter(int seconds) {
        deadline = TIMER.schedule(() -> { expired = true; cancelPending(); }, seconds, java.util.concurrent.TimeUnit.SECONDS);
    }

    public void closeDeadline() { if (deadline != null) deadline.cancel(false); }
    public boolean isDeadlineExceeded() { return expired; }
    private void cancelPending() { for (CompletableFuture<?> future : tracked) future.cancel(true); }

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final Set<CompletableFuture<?>> tracked = ConcurrentHashMap.newKeySet();

    public boolean cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return false;
        }
        cancelPending();
        return true;
    }

    public boolean isCancellationRequested() {
        return cancelled.get();
    }

    public void throwIfCancelled() {
        if (isCancellationRequested()) {
            throw new CancellationException("AI generation cancelled");
        }
        if (expired) throw new AiRunStoppedException("budget_exhausted", "AI time budget exhausted; the draft is retained for recovery.");
    }

    public <T> CompletableFuture<T> track(CompletableFuture<T> future) {
        if (future == null) {
            throw new IllegalArgumentException("future must not be null");
        }
        if (isCancellationRequested() || expired) {
            future.cancel(true);
            throwIfCancelled();
        }
        tracked.add(future);
        future.whenComplete((result, error) -> tracked.remove(future));
        if (isCancellationRequested() || expired) {
            future.cancel(true);
        }
        return future;
    }
}
