package com.nododiiiii.ponderer.forge.sticksnapshot.snapshot;

import net.minecraft.server.level.ServerPlayer;

public final class ReplayStorageGuard {
    private ReplayStorageGuard() {
    }

    public static void writePlayerPersistentData(ServerPlayer player, String path, Runnable write) {
        if (ReplayGuard.shouldBlockPlayer(player)) {
            ReplayGuard.auditBlocked("storage", path, player.getScoreboardName());
            return;
        }
        write.run();
    }
}
