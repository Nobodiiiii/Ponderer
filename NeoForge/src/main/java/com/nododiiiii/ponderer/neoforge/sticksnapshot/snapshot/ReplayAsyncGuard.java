package com.nododiiiii.ponderer.neoforge.sticksnapshot.snapshot;

import net.minecraft.server.level.ServerLevel;

public final class ReplayAsyncGuard {
    private ReplayAsyncGuard() {
    }

    public static Runnable wrap(String source, Runnable task) {
        ReplayGuard.CapturedContext captured = ReplayGuard.captureCurrentContext();
        if (captured == null) {
            return task;
        }

        return () -> {
            if (!ReplayGuard.isSessionActive(captured.sessionId())) {
                ReplayGuard.auditDroppedAsync(source, captured.sessionId());
                return;
            }

            try (ReplayGuard.Scope ignored = ReplayGuard.openFromCapture(captured, source)) {
                task.run();
            }
        };
    }

    public static void execute(ServerLevel level, String source, Runnable task) {
        level.getServer().execute(wrap(source, task));
    }
}
