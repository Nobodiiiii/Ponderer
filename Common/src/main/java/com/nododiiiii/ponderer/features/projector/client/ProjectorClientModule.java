package com.nododiiiii.ponderer.features.projector.client;

import com.nododiiiii.ponderer.application.SceneRefreshCoordinator;

public final class ProjectorClientModule implements AutoCloseable {
    private static boolean active;
    private final AutoCloseable refresh;
    private boolean closed;
    public ProjectorClientModule() {
        active = true;
        refresh = SceneRefreshCoordinator.subscribe(ProjectorClientCaches::invalidateAll);
    }
    public static boolean isActive() { return active; }
    @Override public void close() throws Exception {
        if (closed) return;
        closed = true;
        active = false;
        try { refresh.close(); } finally { ProjectorClientCaches.invalidateAll(); }
    }
}
