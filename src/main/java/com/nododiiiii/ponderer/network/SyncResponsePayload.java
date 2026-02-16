package com.nododiiiii.ponderer.network;

import com.mojang.logging.LogUtils;

import com.nododiiiii.ponderer.ponder.PondererClientCommands;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ponder.SyncMeta;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public record SyncResponsePayload(List<FileEntry> scripts, List<FileEntry> structures) {

    private static final Logger LOGGER = LogUtils.getLogger();

    public record FileEntry(String id, byte[] bytes) {
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(scripts().size());
        for (FileEntry entry : scripts()) {
            buf.writeUtf(entry.id());
            buf.writeByteArray(entry.bytes());
        }
        buf.writeVarInt(structures().size());
        for (FileEntry entry : structures()) {
            buf.writeUtf(entry.id());
            buf.writeByteArray(entry.bytes());
        }
    }

    public static SyncResponsePayload decode(FriendlyByteBuf buf) {
        int scriptsSize = buf.readVarInt();
        List<FileEntry> scripts = new ArrayList<>(scriptsSize);
        for (int i = 0; i < scriptsSize; i++) {
            scripts.add(new FileEntry(buf.readUtf(), buf.readByteArray()));
        }
        int structuresSize = buf.readVarInt();
        List<FileEntry> structures = new ArrayList<>(structuresSize);
        for (int i = 0; i < structuresSize; i++) {
            structures.add(new FileEntry(buf.readUtf(), buf.readByteArray()));
        }
        return new SyncResponsePayload(scripts, structures);
    }

    public static void handle(SyncResponsePayload payload) {
        Path scriptsDir = SceneStore.getSceneDir();
        Path structuresDir = SceneStore.getStructureDir();

        try {
            Files.createDirectories(scriptsDir);
            Files.createDirectories(structuresDir);
        } catch (Exception e) {
            LOGGER.error("Failed to create client sync dirs", e);
            return;
        }

        String pullMode = PondererClientCommands.consumePullMode();
        int written = 0;
        int skipped = 0;
        int conflicts = 0;
        boolean isCheckMode = "check".equals(pullMode);
        java.util.Map<String, byte[]> syncedHashes = new java.util.HashMap<>();

        for (FileEntry entry : payload.scripts()) {
            String metaKey = "scripts/" + entry.id();
            Path localFile = resolveLocalPath(scriptsDir, entry.id(), ".json");

            if (!"force".equals(pullMode)) {
                String status = SyncMeta.checkConflict(metaKey, entry.bytes(), localFile);
                if ("both_modified".equals(status)) {
                    conflicts++;
                    if (isCheckMode) {
                        notifyClient(Component.translatable("ponderer.cmd.pull.conflict_both", entry.id()));
                        skipped++;
                        continue;
                    }
                    if ("keep_local".equals(pullMode)) {
                        skipped++;
                        continue;
                    }
                    notifyClient(Component.translatable("ponderer.cmd.pull.conflict_server", entry.id()));
                } else if ("local_modified".equals(status) && "keep_local".equals(pullMode)) {
                    skipped++;
                    continue;
                }
            }

            writeFile(scriptsDir, entry.id(), entry.bytes(), ".json");
            syncedHashes.put(metaKey, entry.bytes());
            written++;
        }

        for (FileEntry entry : payload.structures()) {
            String metaKey = "structures/" + entry.id();
            Path localFile = resolveLocalPath(structuresDir, entry.id(), ".nbt");

            if (!"force".equals(pullMode)) {
                String status = SyncMeta.checkConflict(metaKey, entry.bytes(), localFile);
                if ("both_modified".equals(status)) {
                    conflicts++;
                    if (isCheckMode) {
                        notifyClient(Component.translatable("ponderer.cmd.pull.conflict_both", entry.id()));
                        skipped++;
                        continue;
                    }
                    if ("keep_local".equals(pullMode)) {
                        skipped++;
                        continue;
                    }
                    notifyClient(Component.translatable("ponderer.cmd.pull.conflict_server", entry.id()));
                } else if ("local_modified".equals(status) && "keep_local".equals(pullMode)) {
                    skipped++;
                    continue;
                }
            }

            writeFile(structuresDir, entry.id(), entry.bytes(), ".nbt");
            syncedHashes.put(metaKey, entry.bytes());
            written++;
        }

        SyncMeta.recordHashes(syncedHashes);

        SceneStore.reloadFromDisk();
        Minecraft.getInstance().execute(PonderIndex::reload);

        notifyClient(Component.translatable("ponderer.cmd.pull.done", written, skipped, conflicts));
        if (conflicts > 0 && isCheckMode) {
            notifyClient(Component.translatable("ponderer.cmd.pull.hint_force"));
            notifyClient(Component.translatable("ponderer.cmd.pull.hint_keep"));
        }
    }

    private static Path resolveLocalPath(Path root, String id, String ext) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        if (loc == null) return root.resolve(id + ext);
        return loc.getNamespace().equals("ponderer")
            ? root.resolve(loc.getPath() + ext)
            : root.resolve(loc.getNamespace()).resolve(loc.getPath() + ext);
    }

    private static void notifyClient(Component message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(message, false);
        }
    }

    private static void writeFile(Path root, String id, byte[] bytes, String ext) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        if (loc == null) {
            LOGGER.warn("Invalid id from server: {}", id);
            return;
        }
        Path path = loc.getNamespace().equals("ponderer")
            ? root.resolve(loc.getPath() + ext)
            : root.resolve(loc.getNamespace()).resolve(loc.getPath() + ext);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (Exception e) {
            LOGGER.warn("Failed to write file: {}", path, e);
        }
    }
}
