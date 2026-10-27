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
    private final Object commitLock = new Object();
    private boolean committed;

    public void expireAfter(int seconds) {
        deadline = TIMER.schedule(() -> {
            synchronized (commitLock) {
                if (committed) return;
                expired = true;
            }
            cancelPending();
        }, seconds, java.util.concurrent.TimeUnit.SECONDS);
    }

    public void closeDeadline() { if (deadline != null) deadline.cancel(false); }
    public boolean isDeadlineExceeded() { return expired; }
    private void cancelPending() {
        for (CompletableFuture<?> future : tracked) future.cancel(true);
        for (AutoCloseable resource : resources) closeResource(resource);
    }
    private final Set<AutoCloseable> resources = ConcurrentHashMap.newKeySet();
    <T extends AutoCloseable> T trackResource(T resource) {
        resources.add(resource);
        if (cancelled.get() || expired) { closeResource(resource); resources.remove(resource); throwIfCancelled(); }
        return resource;
    }
    void releaseResource(AutoCloseable resource) { resources.remove(resource); }
    private static void closeResource(AutoCloseable resource) {
        try { resource.close(); } catch (Exception ignored) { }
    }

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final Set<CompletableFuture<?>> tracked = ConcurrentHashMap.newKeySet();

    public boolean cancel() {
        synchronized (commitLock) {
            if (committed || !cancelled.compareAndSet(false, true)) return false;
        }
        cancelPending();
        return true;
    }

    /** Cancellation and the final disk commit have one ordering point. Acquire repository locks first. */
    public <T> T commit(java.util.concurrent.Callable<T> action) throws Exception {
        synchronized (commitLock) {
            throwIfCancelled();
            T result = action.call();
            committed = true;
            closeDeadline();
            return result;
        }
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
