package com.nododiiiii.ponderer.network;

import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.ponder.UploadPermissions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public record BlueprintConfigUpdatePayload(boolean enableBuiltinItem) implements CustomPacketPayload {

    public static final Type<BlueprintConfigUpdatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "blueprint_config_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintConfigUpdatePayload> CODEC =
            StreamCodec.of(BlueprintConfigUpdatePayload::encode, BlueprintConfigUpdatePayload::decode);

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, BlueprintConfigUpdatePayload payload) {
        buf.writeBoolean(payload.enableBuiltinItem());
    }

    private static BlueprintConfigUpdatePayload decode(RegistryFriendlyByteBuf buf) {
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
