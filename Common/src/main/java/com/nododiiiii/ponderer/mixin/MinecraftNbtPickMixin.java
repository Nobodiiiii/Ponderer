package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.ui.CoordPickState;
import com.nododiiiii.ponderer.ui.NbtPickState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts vanilla middle-click pick behavior while NBT pick or coord pick mode is active,
 * and routes middle click to our capture flow.
 */
@Mixin(Minecraft.class)
public class MinecraftNbtPickMixin {

    // Optional injection: avoid hard crash when runtime mapping cannot resolve target method.
    @Inject(method = "handleKeybinds", at = @At("HEAD"), require = 0)
    private void ponderer$interceptMiddlePickForNbtCapture(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player == null || mc.screen != null) {
            return;
        }

        if (NbtPickState.isActive()) {
            while (mc.options.keyPickItem.consumeClick()) {
                NbtPickState.handleUseClick();
            }
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("ponderer.ui.nbt_pick.middle_prompt"), true);
            return;
        }

        if (CoordPickState.isActive()) {
            CoordPickState.onClientTick();
            while (mc.options.keyPickItem.consumeClick()) {
                CoordPickState.handleMiddleClick();
            }
        }
    }
}
