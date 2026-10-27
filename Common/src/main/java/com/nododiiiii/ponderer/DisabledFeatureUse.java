package com.nododiiiii.ponderer;

import com.nododiiiii.ponderer.feedback.FeedbackLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

public final class DisabledFeatureUse {

    private DisabledFeatureUse() {
    }

    public static void consumeOne(@Nullable Player player, ItemStack stack, String messageKey) {
        if (!stack.isEmpty()) {
            stack.shrink(1);
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            com.nododiiiii.ponderer.platform.PondererServices.NETWORK.sendToPlayer(serverPlayer,
                new com.nododiiiii.ponderer.application.protocol.FeedbackMessagePayload(Component.translatable(messageKey),
                    FeedbackLevel.ERROR, true));
        }
    }
}
