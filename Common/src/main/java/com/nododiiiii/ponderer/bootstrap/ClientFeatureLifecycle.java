package com.nododiiiii.ponderer.bootstrap;

import com.nododiiiii.ponderer.feedback.WorldFeedback;
import com.nododiiiii.ponderer.editor.capture.EditorWorldTools;

public final class ClientFeatureLifecycle {
    private static Object connection;
    private static Object world;
    private ClientFeatureLifecycle() {}
    public static void tick() {
        var client = net.minecraft.client.Minecraft.getInstance();
        Object currentConnection = client.getConnection();
        Object currentWorld = client.level;
        if (connection != currentConnection) {
            connection = currentConnection;
            world = currentWorld;
            ClientCatalogBindings.resetConnection();
        } else if (world != currentWorld) {
            world = currentWorld;
            ClientCatalogBindings.resetWorld();
        }
        WorldFeedback.beginTick();
        if (com.nododiiiii.ponderer.editor.api.EditorModule.isInstalled()) EditorWorldTools.tick();
        WorldFeedback.tick();
    }
}
