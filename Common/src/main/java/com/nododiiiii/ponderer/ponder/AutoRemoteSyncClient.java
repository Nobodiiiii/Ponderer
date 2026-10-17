package com.nododiiiii.ponderer.ponder;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.network.AutoSyncPreferencePayload;
import com.nododiiiii.ponderer.network.SyncResponsePayload;
import com.nododiiiii.ponderer.platform.PondererServices;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/** Resolves the saved client preference without rewriting it when joining a world. */
public final class AutoRemoteSyncClient {
    @Nullable
    private static Object connection;
    @Nullable
    private static Boolean sentEnabled;

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
            SyncResponsePayload.stopAutomaticSync();
        }
        if ((sentEnabled != null && sentEnabled == enabled)
            || !PondererServices.NETWORK.canSendToServer(AutoSyncPreferencePayload.TYPE)) {
            return;
        }
        PondererServices.NETWORK.sendToServer(new AutoSyncPreferencePayload(enabled));
        sentEnabled = enabled;
    }
}
