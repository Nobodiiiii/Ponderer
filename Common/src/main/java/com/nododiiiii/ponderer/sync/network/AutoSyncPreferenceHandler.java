package com.nododiiiii.ponderer.sync.network;

import com.nododiiiii.ponderer.sync.server.AutoRemoteSyncService;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import com.nododiiiii.ponderer.sync.protocol.AutoSyncPreferencePayload;
import static com.nododiiiii.ponderer.sync.protocol.AutoSyncPreferencePayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class AutoSyncPreferenceHandler {
    private AutoSyncPreferenceHandler() {}

    public static void handle(AutoSyncPreferencePayload payload, @Nullable ServerPlayer player) {
        if (player != null) {
            AutoRemoteSyncService.setPlayerEnabled(player, payload.enabled());
        }
    }
}
