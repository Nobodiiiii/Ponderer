package com.nododiiiii.ponderer.network;

import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.ponder.UploadPermissions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public record BlueprintConfigUpdatePayload(boolean enableBuiltinItem) {

    private static final Logger LOGGER = LogUtils.getLogger();

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(enableBuiltinItem());
    }

    public static BlueprintConfigUpdatePayload decode(FriendlyByteBuf buf) {
        return new BlueprintConfigUpdatePayload(buf.readBoolean());
    }

    public static void handle(BlueprintConfigUpdatePayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }

        if (!UploadPermissions.canManage(player)) {
            BlueprintConfigResponsePayload.sendCurrentState(
                player,
                "ponderer.ui.function_page.blueprint_item.admin_required",
                true);
            return;
        }

        try {
            Config.ENABLE_BLUEPRINT_ITEM.set(payload.enableBuiltinItem());
            Config.SERVER_SPEC.save();
            BlueprintConfigResponsePayload.sendCurrentState(
                player,
                payload.enableBuiltinItem()
                    ? "ponderer.ui.function_page.blueprint_item.saved.enabled"
                    : "ponderer.ui.function_page.blueprint_item.saved.disabled",
                false);
        } catch (Exception e) {
            LOGGER.warn("Failed to update Ponderer blueprint server config", e);
            BlueprintConfigResponsePayload.sendCurrentState(
                player,
                "ponderer.ui.function_page.blueprint_item.error",
                true);
        }
    }
}
