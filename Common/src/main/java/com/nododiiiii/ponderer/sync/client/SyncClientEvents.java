package com.nododiiiii.ponderer.sync.client;

import java.util.concurrent.CopyOnWriteArrayList;

public final class SyncClientEvents {
    private static final CopyOnWriteArrayList<Runnable> updates = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Runnable> workspace = new CopyOnWriteArrayList<>();
    private SyncClientEvents() {}
    public static AutoCloseable onUpdatesChanged(Runnable listener) {
        updates.add(listener);
        return () -> updates.remove(listener);
    }
    public static void updatesChanged() { notifyListeners(updates); }
    public static AutoCloseable onWorkspaceChanged(Runnable listener) {
        workspace.add(listener);
        return () -> workspace.remove(listener);
    }
    /** Complete every publication callback, retaining failure for the operation's final result. */
    @javax.annotation.Nullable
    public static RuntimeException workspaceChanged() {
        RuntimeException failure = null;
        for (Runnable listener : workspace) {
            try { listener.run(); }
            catch (RuntimeException error) {
                if (failure == null) failure = error;
                else if (failure != error) failure.addSuppressed(error);
            }
        }
        return failure;
    }
    private static void notifyListeners(Iterable<Runnable> listeners) {
        for (Runnable listener : listeners) {
            try { listener.run(); }
            catch (RuntimeException failure) { com.mojang.logging.LogUtils.getLogger().warn("Sync listener failed", failure); }
        }
    }
}
