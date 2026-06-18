package com.nododiiiii.ponderer;

import net.minecraft.ChatFormatting;
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
        if (player != null) {
            player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.RED), true);
        }
    }
}
