package com.nododiiiii.ponderer.editor.mixin;

import com.nododiiiii.ponderer.editor.capture.InterfaceCapture;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class InterfaceItemCaptureMixin {
    @Inject(method = "useItem", at = @At("HEAD"))
    private void ponderer$item(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> ci) {
        InterfaceCapture.useHand(hand);
    }
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void ponderer$itemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> ci) {
        InterfaceCapture.useHand(hand);
    }
}
