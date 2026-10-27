package com.nododiiiii.ponderer.bootstrap;

import com.nododiiiii.ponderer.application.SceneRefreshCoordinator;
import com.nododiiiii.ponderer.features.projector.client.ProjectorClientCaches;

public final class ClientCatalogBindings {
    private static AutoCloseable projector;
    private static AutoCloseable input;
    private static AutoCloseable sandbox;
    private static AutoCloseable menus;
    private static AutoCloseable preferences;
    private static com.nododiiiii.ponderer.editor.api.EditorModule editor;
    private ClientCatalogBindings() {}
    public static void install() {
        if (sandbox == null) sandbox = com.nododiiiii.ponderer.runtime.interfaces.platform.InterfacePlatform.install(
            com.nododiiiii.ponderer.platform.PondererServices.load(com.nododiiiii.ponderer.runtime.interfaces.platform.InterfaceSandboxPlatform.class));
        if (menus == null) menus = com.nododiiiii.ponderer.runtime.interfaces.InterfaceExtensions.installMenus(
            new com.nododiiiii.ponderer.compat.ae2.RuntimeAe2Menus());
        if (editor == null) editor = new com.nododiiiii.ponderer.editor.api.EditorModule();
        if (preferences == null) preferences = com.nododiiiii.ponderer.runtime.api.PlaybackPreferences.install(
            () -> com.nododiiiii.ponderer.Config.DEFAULT_TEXT_PROGRESS.get(), value -> {
                com.nododiiiii.ponderer.Config.DEFAULT_TEXT_PROGRESS.set(value);
                com.nododiiiii.ponderer.Config.DEFAULT_TEXT_PROGRESS.save();
            });
        if (input == null) input = com.nododiiiii.ponderer.runtime.interfaces.InterfaceExtensions.installIngredients(
            new com.nododiiiii.ponderer.compat.jei.RuntimeJeiIngredients());
        if (projector == null) projector = SceneRefreshCoordinator.subscribe(ProjectorClientCaches::invalidateAll);
    }
}
