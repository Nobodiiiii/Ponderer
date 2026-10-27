package com.nododiiiii.ponderer.bootstrap;

import com.nododiiiii.ponderer.feedback.WorldFeedback;
import com.nododiiiii.ponderer.editor.capture.EditorWorldTools;

public final class ClientFeatureLifecycle {
    private static Object connection;
    private static Object world;
    private ClientFeatureLifecycle() {}
    public static void tick() {
        var client = net.minecraft.client.Minecraft.getInstance();
        if (connection != client.getConnection() || world != client.level) {
            connection = client.getConnection();
            world = client.level;
            ClientCatalogBindings.resetWorld();
        }
        WorldFeedback.beginTick();
        if (com.nododiiiii.ponderer.editor.api.EditorModule.isInstalled()) EditorWorldTools.tick();
        WorldFeedback.tick();
    }
}
