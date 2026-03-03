package com.nododiiiii.ponderer.ponder;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class UploadPermissions {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ALLOWLIST_FILE = "upload_allowlist.txt";

    private UploadPermissions() {
    }

    public static boolean canUpload(ServerPlayer player) {
        if (player.hasPermissions(2)) {
            return true;
        }
        MinecraftServer server = player.server;
        Path path = server.getWorldPath(LevelResource.ROOT)
            .resolve("ponderer")
            .resolve(ALLOWLIST_FILE);

        Set<String> allowlist = loadAllowlist(path);
        if (allowlist.isEmpty()) {
            return false;
        }

        String uuid = player.getUUID().toString().toLowerCase();
        String name = player.getGameProfile().getName().toLowerCase();
        return allowlist.contains(uuid) || allowlist.contains(name);
    }

    private static Set<String> loadAllowlist(Path path) {
        if (!Files.exists(path)) {
            return Set.of();
        }
        try {
            List<String> lines = Files.readAllLines(path);
            Set<String> set = new HashSet<>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                set.add(trimmed.toLowerCase());
            }
            return set;
        } catch (Exception e) {
            LOGGER.warn("Failed to read upload allowlist: {}", path, e);
            return Set.of();
        }
    }
}