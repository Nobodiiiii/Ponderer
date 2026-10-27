package com.nododiiiii.ponderer.sync.client;

public final class SyncClientModule implements AutoCloseable {
    private static boolean active;
    private boolean closed;
    private final AutoCloseable workspace;
    public SyncClientModule(Runnable refreshWorkspace) {
        workspace = SyncClientEvents.onWorkspaceChanged(refreshWorkspace);
        active = true;
    }
    public static boolean isActive() { return active; }
    @Override public void close() {
        if (closed) return;
        closed = true;
        active = false;
        try { workspace.close(); }
        catch (Exception error) { throw new IllegalStateException("Failed to release sync workspace binding", error); }
        finally { AutoRemoteSyncClient.resetConnection(); }
    }
}
