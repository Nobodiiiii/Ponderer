package com.nododiiiii.ponderer.features.projector.client;

import java.util.function.Function;

public final class ProjectorClientModule implements AutoCloseable {
    private static boolean active;
    private final AutoCloseable refresh;
    private boolean closed;
    public ProjectorClientModule(Function<Runnable, AutoCloseable> subscribe) {
        active = true;
        refresh = subscribe.apply(ProjectorClientCaches::invalidateAll);
    }
    public static boolean isActive() { return active; }
    @Override public void close() throws Exception {
        if (closed) return;
        closed = true;
        active = false;
        try { refresh.close(); } finally { ProjectorClientCaches.invalidateAll(); }
    }
}
