package com.nododiiiii.ponderer.bootstrap;

import com.nododiiiii.ponderer.application.SceneRefreshCoordinator;
import com.nododiiiii.ponderer.projector.client.ProjectorClientCaches;

public final class ClientCatalogBindings {
    private static AutoCloseable projector;
    private ClientCatalogBindings() {}
    public static void install() {
        if (projector == null) projector = SceneRefreshCoordinator.subscribe(ProjectorClientCaches::invalidateAll);
    }
}
