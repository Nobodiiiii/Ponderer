package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.ponder.SyncMeta;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;


/**
 * Server -> Client response after an upload (push) attempt.
 * Status format:
 *   "ok:<newHash>"   - success, client should update SyncMeta
 *   "conflict"       - server file was modified, push rejected
 *   "error"          - write failed
 */
public record UploadResponsePayload(String sceneId, String status) implements CustomPacketPayload {
    public static final Type<UploadResponsePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "upload_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UploadResponsePayload> CODEC =
        StreamCodec.of(UploadResponsePayload::encode, UploadResponsePayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, UploadResponsePayload payload) {
        buf.writeUtf(payload.sceneId());
        buf.writeUtf(payload.status());
    }

    private static UploadResponsePayload decode(RegistryFriendlyByteBuf buf) {
        return new UploadResponsePayload(buf.readUtf(), buf.readUtf());
    }

    public static void handle(UploadResponsePayload payload) {
        if (payload.status() != null && payload.status().startsWith("ok:")) {
            // Update SyncMeta with the new hash so next push/pull knows the sync point
            String newHash = payload.status().substring(3);
            String metaKey = "scripts/" + payload.sceneId();

            // Also record the local file hash as the sync point
            java.nio.file.Path localFile = resolveLocalScenePath(payload.sceneId());
            if (localFile != null && java.nio.file.Files.exists(localFile)) {
                try {
                    byte[] bytes = java.nio.file.Files.readAllBytes(localFile);
                    SyncMeta.recordHash(metaKey, bytes);
                } catch (Exception ignored) {
                    // fallback: use the server hash directly
                    java.util.Map<String, String> meta = SyncMeta.load();
                    meta.put(metaKey, newHash);
                    SyncMeta.save(meta);
                }
            }
        } else if ("conflict".equals(payload.status())) {
            notifyClient(net.minecraft.network.chat.Component.translatable("ponderer.cmd.push.conflict", payload.sceneId()));
        }
    }

    private static java.nio.file.Path resolveLocalScenePath(String sceneId) {
        ResourceLocation loc = ResourceLocation.tryParse(sceneId);
        if (loc == null) return null;
        java.nio.file.Path dir = com.nododiiiii.ponderer.ponder.SceneStore.getSceneDir();
        return loc.getNamespace().equals(Ponderer.MODID)
            ? dir.resolve(loc.getPath() + ".json")
            : dir.resolve(loc.getNamespace()).resolve(loc.getPath() + ".json");
    }

    private static void notifyClient(net.minecraft.network.chat.Component message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(message, false);
        }
    }
}
