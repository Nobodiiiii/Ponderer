package com.nododiiiii.ponderer.sync.network;

import com.nododiiiii.ponderer.feedback.FeedbackLevel;
import com.nododiiiii.ponderer.feedback.ClientFeedback;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.sync.client.PendingRemoteUpdates;
import com.nododiiiii.ponderer.sync.client.SyncMeta;
import com.nododiiiii.ponderer.editor.screen.RemoteBrowserScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.nododiiiii.ponderer.sync.protocol.UploadResponsePayload;
import static com.nododiiiii.ponderer.sync.protocol.UploadResponsePayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class UploadResponseHandler {
    private UploadResponseHandler() {}

    public static void handle(UploadResponsePayload payload) {
        if (payload.status() != null && payload.status().startsWith("ok:")) {
            String newHash = payload.status().substring(3);
            String metaKey = SyncMeta.metaKey("scripts", payload.sceneId(), payload.pack());
            PendingRemoteUpdates.resolved(metaKey);

            java.nio.file.Path localFile = SceneStore.findLocalSceneFile(payload.sceneId(), payload.pack());
            if (localFile != null && java.nio.file.Files.exists(localFile)) {
                try {
                    byte[] bytes = java.nio.file.Files.readAllBytes(localFile);
                    SyncMeta.recordHash(metaKey, bytes);
                } catch (Exception ignored) {
                    java.util.Map<String, String> meta = SyncMeta.load();
                    meta.put(metaKey, newHash);
                    SyncMeta.save(meta);
                }
            }
        }
        String display = SceneStore.displaySceneKey(payload.sceneId(), payload.pack());
        boolean success = payload.status() != null && payload.status().startsWith("ok:");
        String key = success ? "ponderer.cmd.push.upload_ok" : switch (payload.status() == null ? "" : payload.status()) {
            case "conflict" -> "ponderer.cmd.push.conflict";
            case "denied" -> "ponderer.cmd.push.no_permission";
            default -> "ponderer.cmd.push.upload_failed";
        };
        ClientFeedback.result(Component.translatable(key, display),
            success ? FeedbackLevel.SUCCESS : FeedbackLevel.ERROR);
        if (ClientFeedback.responseScreen() instanceof RemoteBrowserScreen screen
            && Minecraft.getInstance().screen == screen) screen.refreshAfterUpload(success);
    }
}
