package com.nododiiiii.ponderer.editor.mixin;

import com.nododiiiii.ponderer.editor.capture.InterfaceCapture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public class InterfaceBlockCaptureMixin {
    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void ponderer$itemOn(ItemStack stack, Level level, Player player, InteractionHand hand,
                                 BlockHitResult hit, CallbackInfoReturnable<ItemInteractionResult> ci) {
        InterfaceCapture.observeBlockUse(level, hit, ci.getReturnValue().consumesAction());
    }

    @Inject(method = "useWithoutItem", at = @At("RETURN"))
    private void ponderer$withoutItem(Level level, Player player, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> ci) {
        InterfaceCapture.observeBlockUse(level, hit, ci.getReturnValue().consumesAction());
    }
}
