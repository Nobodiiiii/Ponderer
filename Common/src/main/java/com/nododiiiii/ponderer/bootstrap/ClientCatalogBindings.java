package com.nododiiiii.ponderer.bootstrap;
import com.nododiiiii.ponderer.application.network.FeedbackResponseDispatch;

import com.nododiiiii.ponderer.features.projector.client.ProjectorClientCaches;

public final class ClientCatalogBindings {
    private static AutoCloseable projector;
    private static AutoCloseable syncUpdates;
    private static com.nododiiiii.ponderer.sync.client.SyncClientModule sync;
    private static AutoCloseable feedback;
    private static AutoCloseable input;
    private static AutoCloseable player;
    private static AutoCloseable menus;
    private static AutoCloseable preferences;
    private static com.nododiiiii.ponderer.editor.api.EditorModule editor;
    private ClientCatalogBindings() {}
    public static void install() {
        com.nododiiiii.ponderer.application.api.RuntimeAccess.install(ProductRuntime::engine);
        com.nododiiiii.ponderer.application.ai.AiSceneGenerator.startModule();
        if (sync == null) sync = new com.nododiiiii.ponderer.sync.client.SyncClientModule(
            com.nododiiiii.ponderer.application.SceneRefreshCoordinator::refresh);
        if (player == null) player = com.nododiiiii.ponderer.runtime.api.RuntimeFeatures.requestPlayerEnhancements("ponderer");
        if (com.nododiiiii.ponderer.runtime.api.RuntimeFeatures.interfacesEnabled() && menus == null) menus = com.nododiiiii.ponderer.runtime.interfaces.InterfaceExtensions.installMenus(
            new com.nododiiiii.ponderer.compat.ae2.RuntimeAe2Menus());
        if (com.nododiiiii.ponderer.bootstrap.launch.ProductFeatures.enabled(com.nododiiiii.ponderer.bootstrap.launch.ProductFeature.EDITOR) && editor == null)
            editor = new com.nododiiiii.ponderer.editor.api.EditorModule(new com.nododiiiii.ponderer.compat.jei.EditorJeiIngredients());
        if (preferences == null) preferences = com.nododiiiii.ponderer.runtime.api.PlaybackPreferences.install(
            () -> com.nododiiiii.ponderer.Config.DEFAULT_TEXT_PROGRESS.get(), value -> {
                com.nododiiiii.ponderer.Config.DEFAULT_TEXT_PROGRESS.set(value);
                com.nododiiiii.ponderer.Config.DEFAULT_TEXT_PROGRESS.save();
            });
        if (input == null) input = com.nododiiiii.ponderer.runtime.interfaces.InterfaceExtensions.installIngredients(
            new com.nododiiiii.ponderer.compat.jei.RuntimeJeiIngredients());
        if (syncUpdates == null) syncUpdates = com.nododiiiii.ponderer.sync.client.SyncClientEvents.onUpdatesChanged(() -> {
            if (net.minecraft.client.Minecraft.getInstance().screen instanceof com.nododiiiii.ponderer.application.ui.RemoteBrowserScreen screen) screen.onSyncUpdatesChanged();
        });
        if (feedback == null) feedback = com.nododiiiii.ponderer.feedback.ClientFeedback.installResponseHandler(
            com.nododiiiii.ponderer.application.network.FeedbackResponseDispatch::handleResponse);
        com.nododiiiii.ponderer.features.blueprint.BlueprintHandler.installPrompt(() ->
            net.minecraft.client.Minecraft.getInstance().setScreen(new com.nododiiiii.ponderer.application.ui.BlueprintPromptScreen()));
        if (com.nododiiiii.ponderer.runtime.api.RuntimeFeatures.enabled(com.nododiiiii.ponderer.runtime.api.RuntimeFeature.DETACHED_RENDERING) && projector == null) projector = new com.nododiiiii.ponderer.features.projector.client.ProjectorClientModule(
            com.nododiiiii.ponderer.application.SceneRefreshCoordinator::subscribe);
    }

    public static void stopAi() {
        com.nododiiiii.ponderer.runtime.support.lifecycle.Cleanup.all(
            com.nododiiiii.ponderer.application.ai.AiSceneGenerator::stopModule,
            com.nododiiiii.ponderer.application.ui.AiGenerateScreen::resetGenerationScope);
    }
    public static void stopSync() {
        if (sync != null) { sync.close(); sync = null; }
    }
    public static void stopProjector() {
        if (projector == null) return;
        try { projector.close(); } catch (Exception error) { throw new IllegalStateException(error); }
        finally { projector = null; }
    }
    public static void stopEditor() {
        if (editor != null) {
            try { editor.close(); } finally { editor = null; }
        }
    }
    public static void close() {
        com.nododiiiii.ponderer.application.JavaSceneWorkspace.close();
        stopAi();
        stopSync();
        RuntimeException failure = null;
        try { stopEditor(); } catch (RuntimeException error) { failure = error; }
        for (AutoCloseable binding : new AutoCloseable[]{projector, input, player, menus, preferences, syncUpdates, feedback}) {
            if (binding == null) continue;
            try { binding.close(); } catch (Exception error) {
                if (failure == null) failure = new IllegalStateException("Failed to close client binding");
                failure.addSuppressed(error);
            }
        }
        projector = input = player = menus = preferences = syncUpdates = feedback = null;
        com.nododiiiii.ponderer.features.blueprint.BlueprintHandler.installPrompt(() -> {});
        try { resetConnection(); } catch (RuntimeException error) {
            if (failure == null) failure = error;
            else failure.addSuppressed(error);
        }
        if (failure != null) throw failure;
    }
    /** End connection-owned state as well as any world-owned tools and playback. */
    public static void resetConnection() {
        com.nododiiiii.ponderer.runtime.support.lifecycle.Cleanup.all(
            () -> com.nododiiiii.ponderer.sync.client.AutoRemoteSyncClient.resetConnection(),
            () -> com.nododiiiii.ponderer.feedback.ClientFeedback.resetConnection(),
            ClientCatalogBindings::resetWorld
        );
    }
    /** Dimension changes end world-owned state while preserving requests on the same connection. */
    public static void resetWorld() {
        com.nododiiiii.ponderer.runtime.support.lifecycle.Cleanup.all(
            () -> com.nododiiiii.ponderer.application.ai.AiSceneGenerator.cancelAll(),
            () -> com.nododiiiii.ponderer.application.ui.AiGenerateScreen.resetGenerationScope(),
            () -> { if (com.nododiiiii.ponderer.runtime.api.RuntimeFeatures.interfacesEnabled()) com.nododiiiii.ponderer.runtime.interfaces.host.InterfaceHost.closeAll("world changed"); },
            () -> com.nododiiiii.ponderer.editor.api.EditorModule.resetSession(),
            () -> com.nododiiiii.ponderer.application.ui.SceneEditorScreen.resetSession(),
            () -> com.nododiiiii.ponderer.application.ui.PonderScreenNavigation.resetSession(),
            () -> ProjectorClientCaches.invalidateAll(),
            () -> com.nododiiiii.ponderer.features.trigger.TriggerManager.resetSession(),
            () -> com.nododiiiii.ponderer.features.blueprint.BlueprintHandler.resetSession(),
            () -> com.nododiiiii.ponderer.feedback.WorldFeedback.reset()
        );
    }
}
