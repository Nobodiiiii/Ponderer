package com.nododiiiii.ponderer.sync.client;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.sync.protocol.AutoSyncPreferencePayload;
import com.nododiiiii.ponderer.platform.PondererServices;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/** Resolves the saved client preference without rewriting it when joining a world. */
public final class AutoRemoteSyncClient {
    @Nullable
    private static Object connection;
    @Nullable
    private static Boolean sentEnabled;

    public static void resetConnection() {
        connection = null;
        sentEnabled = null;
        RemotePullClient.resetConnection();
        PendingRemoteUpdates.resetConnection();
    }

    private AutoRemoteSyncClient() {
    }

    public static boolean isEnabled() {
        return switch (Config.AUTO_SYNC_SERVER_PONDERS.get()) {
            case "on" -> true;
            case "off" -> false;
            default -> {
                var server = Minecraft.getInstance().getSingleplayerServer();
                yield server == null || server.isPublished();
            }
        };
    }

    public static void tick() {
        if (!SyncClientModule.isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        Object current = mc.getConnection();
        if (connection != current) {
            connection = current;
            sentEnabled = null;
        }
        if (current == null || mc.player == null) {
            return;
        }
        boolean enabled = isEnabled();
        if (!enabled) {
            RemotePullClient.stopAutomaticSync();
        }
        if ((sentEnabled != null && sentEnabled == enabled)
            || !PondererServices.NETWORK.canSendToServer(AutoSyncPreferencePayload.TYPE)) {
            return;
        }
        PondererServices.NETWORK.sendToServer(new AutoSyncPreferencePayload(enabled));
        sentEnabled = enabled;
    }
}
