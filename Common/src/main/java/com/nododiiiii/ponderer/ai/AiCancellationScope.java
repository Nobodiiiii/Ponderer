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

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final Set<CompletableFuture<?>> tracked = ConcurrentHashMap.newKeySet();

    public boolean cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return false;
        }
        for (CompletableFuture<?> future : tracked) {
            future.cancel(true);
        }
        return true;
    }

    public boolean isCancellationRequested() {
        return cancelled.get();
    }

    public void throwIfCancelled() {
        if (isCancellationRequested()) {
            throw new CancellationException("AI generation cancelled");
        }
    }

    public <T> CompletableFuture<T> track(CompletableFuture<T> future) {
        if (future == null) {
            throw new IllegalArgumentException("future must not be null");
        }
        if (isCancellationRequested()) {
            future.cancel(true);
            throwIfCancelled();
        }
        tracked.add(future);
        future.whenComplete((result, error) -> tracked.remove(future));
        if (isCancellationRequested()) {
            future.cancel(true);
        }
        return future;
    }
}
