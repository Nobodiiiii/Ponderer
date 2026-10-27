package com.nododiiiii.ponderer.sync.client;

import java.util.concurrent.CopyOnWriteArrayList;

public final class SyncClientEvents {
    private static final CopyOnWriteArrayList<Runnable> updates = new CopyOnWriteArrayList<>();
    private SyncClientEvents() {}
    public static AutoCloseable onUpdatesChanged(Runnable listener) {
        updates.add(listener);
        return () -> updates.remove(listener);
    }
    public static void updatesChanged() { for (Runnable listener : updates) listener.run(); }
}
