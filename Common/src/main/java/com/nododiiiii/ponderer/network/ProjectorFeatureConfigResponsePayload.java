package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.UploadPermissions;
import com.nododiiiii.ponderer.ui.ProjectorFeatureConfigScreen;
import com.nododiiiii.ponderer.ui.UIText;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public record ProjectorFeatureConfigResponsePayload(boolean enableProjector, boolean canManage,
                                                    String messageKey, boolean error) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(enableProjector());
        buf.writeBoolean(canManage());
        buf.writeUtf(messageKey() == null ? "" : messageKey());
        buf.writeBoolean(error());
    }

    public static ProjectorFeatureConfigResponsePayload decode(FriendlyByteBuf buf) {
        return new ProjectorFeatureConfigResponsePayload(
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readUtf(),
            buf.readBoolean());
    }

    public static void sendCurrentState(ServerPlayer player, String messageKey, boolean error) {
        PondererServices.NETWORK.sendToPlayer(player, new ProjectorFeatureConfigResponsePayload(
            currentEnableProjector(),
            UploadPermissions.canManage(player),
            messageKey == null ? "" : messageKey,
            error));
    }

    public static void handle(ProjectorFeatureConfigResponsePayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof ProjectorFeatureConfigScreen screen) {
            screen.receiveServerState(payload);
            return;
        }

        if (payload.messageKey() != null && !payload.messageKey().isBlank() && client.player != null) {
            client.player.displayClientMessage(Component.literal(UIText.of(payload.messageKey())), false);
        }
    }

    private static boolean currentEnableProjector() {
        try {
            return Config.ENABLE_PROJECTOR.get();
        } catch (Exception ignored) {
            return false;
        }
    }
}
