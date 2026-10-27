package com.nododiiiii.ponderer.features.blueprint.network;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import com.nododiiiii.ponderer.features.blueprint.protocol.BlueprintConfigRequestPayload;
import static com.nododiiiii.ponderer.features.blueprint.protocol.BlueprintConfigRequestPayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class BlueprintConfigRequestHandler {
    private BlueprintConfigRequestHandler() {}

    public static void handle(BlueprintConfigRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        BlueprintConfigResponseHandler.sendCurrentState(player, "", false);
    }
}
