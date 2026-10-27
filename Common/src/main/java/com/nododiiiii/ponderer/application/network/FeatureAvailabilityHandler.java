package com.nododiiiii.ponderer.application.network;

import com.nododiiiii.ponderer.FeatureAvailability;
import com.nododiiiii.ponderer.platform.PondererServices;
import net.minecraft.server.level.ServerPlayer;
import com.nododiiiii.ponderer.application.protocol.FeatureAvailabilityPayload;
import static com.nododiiiii.ponderer.application.protocol.FeatureAvailabilityPayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class FeatureAvailabilityHandler {
    private FeatureAvailabilityHandler() {}

    public static void sendTo(ServerPlayer player) {
        PondererServices.NETWORK.sendToPlayer(player, new FeatureAvailabilityPayload(
            FeatureAvailability.isBlueprintEnabled(),
            FeatureAvailability.isProjectorEnabled()));
    }

    public static void handle(FeatureAvailabilityPayload payload) {
        FeatureAvailability.captureFromServer(payload.blueprintEnabled(), payload.projectorEnabled());
    }
}
