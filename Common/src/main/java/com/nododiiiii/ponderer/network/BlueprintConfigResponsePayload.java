package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.UploadPermissions;
import com.nododiiiii.ponderer.ui.BlueprintItemConfigScreen;
import com.nododiiiii.ponderer.ui.UIText;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record BlueprintConfigResponsePayload(boolean enableBuiltinItem, boolean canManage,
                                             String messageKey, boolean error) implements CustomPacketPayload {

    public static final Type<BlueprintConfigResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "blueprint_config_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintConfigResponsePayload> CODEC =
            StreamCodec.of(BlueprintConfigResponsePayload::encode, BlueprintConfigResponsePayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, BlueprintConfigResponsePayload payload) {
        buf.writeBoolean(payload.enableBuiltinItem());
        buf.writeBoolean(payload.canManage());
        buf.writeUtf(payload.messageKey() == null ? "" : payload.messageKey());
        buf.writeBoolean(payload.error());
    }

    private static BlueprintConfigResponsePayload decode(RegistryFriendlyByteBuf buf) {
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
