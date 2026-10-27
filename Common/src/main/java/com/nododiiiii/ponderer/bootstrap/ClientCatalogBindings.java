package com.nododiiiii.ponderer.bootstrap;
import com.nododiiiii.ponderer.application.network.FeedbackResponseDispatch;

import com.nododiiiii.ponderer.features.projector.client.ProjectorClientCaches;

public final class ClientCatalogBindings {
    private static AutoCloseable projector;
    private static AutoCloseable syncUpdates;
    private static com.nododiiiii.ponderer.sync.client.SyncClientModule sync;
    private static AutoCloseable feedback;
    private static AutoCloseable input;
    private static AutoCloseable sandbox;
    private static AutoCloseable menus;
    private static AutoCloseable preferences;
    private static com.nododiiiii.ponderer.editor.api.EditorModule editor;
    private ClientCatalogBindings() {}
    public static void install() {
        com.nododiiiii.ponderer.application.api.RuntimeAccess.install(ProductRuntime::engine);
        com.nododiiiii.ponderer.application.ai.AiSceneGenerator.startModule();
        if (sync == null) sync = new com.nododiiiii.ponderer.sync.client.SyncClientModule(
            com.nododiiiii.ponderer.application.SceneRefreshCoordinator::refresh);
        if (sandbox == null) sandbox = com.nododiiiii.ponderer.runtime.interfaces.platform.InterfacePlatform.install(
            com.nododiiiii.ponderer.platform.PondererServices.load(com.nododiiiii.ponderer.runtime.interfaces.platform.InterfaceSandboxPlatform.class));
        if (menus == null) menus = com.nododiiiii.ponderer.runtime.interfaces.InterfaceExtensions.installMenus(
            new com.nododiiiii.ponderer.compat.ae2.RuntimeAe2Menus());
        if (editor == null) editor = new com.nododiiiii.ponderer.editor.api.EditorModule(new com.nododiiiii.ponderer.compat.jei.EditorJeiIngredients());
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
        if (projector == null) projector = new com.nododiiiii.ponderer.features.projector.client.ProjectorClientModule(
            com.nododiiiii.ponderer.application.SceneRefreshCoordinator::subscribe);
    }

    public static void stopAi() {
        com.nododiiiii.ponderer.support.lifecycle.Cleanup.all(
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
        stopAi();
        stopSync();
        RuntimeException failure = null;
        try { stopEditor(); } catch (RuntimeException error) { failure = error; }
        for (AutoCloseable binding : new AutoCloseable[]{projector, input, sandbox, menus, preferences, syncUpdates, feedback}) {
            if (binding == null) continue;
            try { binding.close(); } catch (Exception error) {
                if (failure == null) failure = new IllegalStateException("Failed to close client binding");
                failure.addSuppressed(error);
            }
        }
        projector = input = sandbox = menus = preferences = syncUpdates = feedback = null;
        com.nododiiiii.ponderer.features.blueprint.BlueprintHandler.installPrompt(() -> {});
        try { resetWorld(); } catch (RuntimeException error) {
            if (failure == null) failure = error;
            else failure.addSuppressed(error);
        }
        if (failure != null) throw failure;
    }
    public static void resetWorld() {
        com.nododiiiii.ponderer.support.lifecycle.Cleanup.all(
            () -> com.nododiiiii.ponderer.application.ai.AiSceneGenerator.cancelAll(),
            () -> com.nododiiiii.ponderer.application.ui.AiGenerateScreen.resetGenerationScope(),
            () -> com.nododiiiii.ponderer.sync.client.AutoRemoteSyncClient.resetConnection(),
            () -> com.nododiiiii.ponderer.feedback.ClientFeedback.resetConnection(),
            () -> com.nododiiiii.ponderer.runtime.interfaces.host.InterfaceHost.closeAll("world changed"),
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
