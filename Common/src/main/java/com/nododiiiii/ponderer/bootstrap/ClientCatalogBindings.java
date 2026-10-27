package com.nododiiiii.ponderer.bootstrap;

import com.nododiiiii.ponderer.application.SceneRefreshCoordinator;
import com.nododiiiii.ponderer.projector.client.ProjectorClientCaches;

public final class ClientCatalogBindings {
    private static AutoCloseable projector;
    private static AutoCloseable input;
    private ClientCatalogBindings() {}
    public static void install() {
        if (input == null) input = com.nododiiiii.ponderer.runtime.api.PlaybackInputLocks.register(com.nododiiiii.ponderer.ui.PickState::isActive);
        if (projector == null) projector = SceneRefreshCoordinator.subscribe(ProjectorClientCaches::invalidateAll);
    }
}
