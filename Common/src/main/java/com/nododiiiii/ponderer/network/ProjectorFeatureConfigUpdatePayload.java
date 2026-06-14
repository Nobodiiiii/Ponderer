package com.nododiiiii.ponderer.network;

import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.ponder.UploadPermissions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public record ProjectorFeatureConfigUpdatePayload(boolean enableProjector) {

    private static final Logger LOGGER = LogUtils.getLogger();

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(enableProjector());
    }

    public static ProjectorFeatureConfigUpdatePayload decode(FriendlyByteBuf buf) {
        return new ProjectorFeatureConfigUpdatePayload(buf.readBoolean());
    }

    public static void handle(ProjectorFeatureConfigUpdatePayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }

        if (!UploadPermissions.canManage(player)) {
            ProjectorFeatureConfigResponsePayload.sendCurrentState(
                player,
                "ponderer.ui.function_page.projector.admin_required",
                true);
            return;
        }

        try {
            Config.ENABLE_PROJECTOR.set(payload.enableProjector());
            Config.SERVER_SPEC.save();
            ProjectorFeatureConfigResponsePayload.sendCurrentState(
                player,
                payload.enableProjector()
                    ? "ponderer.ui.function_page.projector.saved.enabled"
                    : "ponderer.ui.function_page.projector.saved.disabled",
                false);
        } catch (Exception e) {
            LOGGER.warn("Failed to update Ponderer projector server config", e);
            ProjectorFeatureConfigResponsePayload.sendCurrentState(
                player,
                "ponderer.ui.function_page.projector.error",
                true);
        }
    }
}
