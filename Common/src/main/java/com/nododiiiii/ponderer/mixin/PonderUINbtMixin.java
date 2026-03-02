package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.ponder.NbtSceneFilter;
import net.createmod.ponder.foundation.PonderTag;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Captures the ItemStack when PonderUI.of(ItemStack) is called,
 * so that compile() can filter scenes by NBT.
 */
@Mixin(PonderUI.class)
public class PonderUINbtMixin {

    @Inject(
        method = "of(Lnet/minecraft/world/item/ItemStack;)Lnet/createmod/ponder/foundation/ui/PonderUI;",
        at = @At("HEAD")
    )
    private static void ponderer$captureStackHead(ItemStack item, CallbackInfoReturnable<PonderUI> cir) {
        NbtSceneFilter.setCurrentStack(item);
    }

    @Inject(
        method = "of(Lnet/minecraft/world/item/ItemStack;)Lnet/createmod/ponder/foundation/ui/PonderUI;",
        at = @At("RETURN")
    )
    private static void ponderer$clearStackReturn(ItemStack item, CallbackInfoReturnable<PonderUI> cir) {
        NbtSceneFilter.clearCurrentStack();
    }

    @Inject(
        method = "of(Lnet/minecraft/world/item/ItemStack;Lnet/createmod/ponder/foundation/PonderTag;)Lnet/createmod/ponder/foundation/ui/PonderUI;",
        at = @At("HEAD")
    )
    private static void ponderer$captureStackTagHead(ItemStack item, PonderTag tag, CallbackInfoReturnable<PonderUI> cir) {
        NbtSceneFilter.setCurrentStack(item);
    }

    @Inject(
        method = "of(Lnet/minecraft/world/item/ItemStack;Lnet/createmod/ponder/foundation/PonderTag;)Lnet/createmod/ponder/foundation/ui/PonderUI;",
        at = @At("RETURN")
    )
    private static void ponderer$clearStackTagReturn(ItemStack item, PonderTag tag, CallbackInfoReturnable<PonderUI> cir) {
        NbtSceneFilter.clearCurrentStack();
    }
}
