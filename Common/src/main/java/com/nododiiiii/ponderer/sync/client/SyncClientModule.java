package com.nododiiiii.ponderer.sync.client;

public final class SyncClientModule implements AutoCloseable {
    private static boolean active;
    private boolean closed;
    public SyncClientModule() { active = true; }
    public static boolean isActive() { return active; }
    @Override public void close() {
        if (closed) return;
        closed = true;
        active = false;
        AutoRemoteSyncClient.resetConnection();
    }
}
