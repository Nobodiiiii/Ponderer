package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.UploadPermissions;
import com.nododiiiii.ponderer.ui.BlueprintItemConfigScreen;
import com.nododiiiii.ponderer.ui.UIText;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public record BlueprintConfigResponsePayload(boolean enableBuiltinItem, boolean canManage,
                                             String messageKey, boolean error) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(enableBuiltinItem());
        buf.writeBoolean(canManage());
        buf.writeUtf(messageKey() == null ? "" : messageKey());
        buf.writeBoolean(error());
    }

    public static BlueprintConfigResponsePayload decode(FriendlyByteBuf buf) {
        return new BlueprintConfigResponsePayload(
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readUtf(),
            buf.readBoolean());
    }

    public static void sendCurrentState(ServerPlayer player, String messageKey, boolean error) {
        PondererServices.NETWORK.sendToPlayer(player, new BlueprintConfigResponsePayload(
            currentEnableBuiltinItem(),
            UploadPermissions.canManage(player),
            messageKey == null ? "" : messageKey,
            error));
    }

    public static void handle(BlueprintConfigResponsePayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof BlueprintItemConfigScreen screen) {
            screen.receiveServerState(payload);
            return;
        }

        if (payload.messageKey() != null && !payload.messageKey().isBlank() && client.player != null) {
            client.player.displayClientMessage(Component.literal(UIText.of(payload.messageKey())), false);
        }
    }

    private static boolean currentEnableBuiltinItem() {
        try {
            return Config.ENABLE_BLUEPRINT_ITEM.get();
        } catch (Exception ignored) {
            return false;
        }
    }
}
