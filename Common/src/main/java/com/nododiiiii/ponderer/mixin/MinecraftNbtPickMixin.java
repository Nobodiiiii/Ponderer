package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.ui.NbtPickState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts vanilla middle-click pick behavior while NBT pick mode is active,
 * and routes middle click to our world NBT capture flow.
 */
@Mixin(Minecraft.class)
public class MinecraftNbtPickMixin {

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void ponderer$interceptMiddlePickForNbtCapture(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player == null || mc.screen != null || !NbtPickState.isActive()) {
            return;
        }

        while (mc.options.keyPickItem.consumeClick()) {
            NbtPickState.handleUseClick();
        }

        mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("ponderer.ui.nbt_pick.middle_prompt"), true);
    }
}
