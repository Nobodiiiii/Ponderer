package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ponder.SyncMeta;
import com.nododiiiii.ponderer.ui.RemoteBrowserScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Server -> Client response after an upload (push) attempt.
 * Status format:
 *   "ok:<newHash>"   - success, client should update SyncMeta
 *   "conflict"       - server file was modified, push rejected
 *   "error"          - write failed
 */
public record UploadResponsePayload(String sceneId, @Nullable String pack, String status) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(sceneId());
        writeOptionalUtf(buf, pack());
        buf.writeUtf(status());
    }

    public static UploadResponsePayload decode(FriendlyByteBuf buf) {
        return new UploadResponsePayload(buf.readUtf(), readOptionalUtf(buf), buf.readUtf());
    }

    public static void handle(UploadResponsePayload payload) {
        if (payload.status() != null && payload.status().startsWith("ok:")) {
            String newHash = payload.status().substring(3);
            String metaKey = SyncMeta.metaKey("scripts", payload.sceneId(), payload.pack());

            java.nio.file.Path localFile = resolveLocalScenePath(payload.sceneId(), payload.pack());
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
            if (Minecraft.getInstance().screen instanceof RemoteBrowserScreen screen) {
                screen.receiveUploadResult(true,
                    "Uploaded " + SceneStore.displaySceneKey(payload.sceneId(), payload.pack()));
            }
        } else if ("conflict".equals(payload.status())) {
            String display = SceneStore.displaySceneKey(payload.sceneId(), payload.pack());
            if (Minecraft.getInstance().screen instanceof RemoteBrowserScreen screen) {
                screen.receiveUploadResult(false, "Remote conflict: " + display);
            } else {
                notifyClient(Component.translatable("ponderer.cmd.push.conflict", display));
            }
        } else if (Minecraft.getInstance().screen instanceof RemoteBrowserScreen screen) {
            screen.receiveUploadResult(false,
                "Upload failed: " + SceneStore.displaySceneKey(payload.sceneId(), payload.pack()));
        }
    }

    private static java.nio.file.Path resolveLocalScenePath(String sceneId, @Nullable String pack) {
        return SceneStore.findLocalSceneFile(sceneId, pack);
    }

    private static void notifyClient(Component message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(message, false);
        }
    }

    private static void writeOptionalUtf(FriendlyByteBuf buf, @Nullable String value) {
        boolean present = value != null && !value.isBlank();
        buf.writeBoolean(present);
        if (present) {
            buf.writeUtf(value);
        }
    }

    @Nullable
    private static String readOptionalUtf(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUtf() : null;
    }
}
