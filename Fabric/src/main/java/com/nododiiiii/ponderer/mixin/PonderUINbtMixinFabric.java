package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.ponder.NbtSceneFilter;
import net.createmod.ponder.foundation.PonderTag;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PonderUI.class)
public class PonderUINbtMixinFabric {

    @Inject(
        method = "of(Lnet/minecraft/world/item/ItemStack;)Lnet/createmod/ponder/foundation/ui/PonderUI;",
        at = @At("HEAD"),
        require = 0
    )
    private static void ponderer$captureStackHeadFabric(ItemStack item, CallbackInfoReturnable<PonderUI> cir) {
        NbtSceneFilter.setCurrentStack(item);
    }

    @Inject(
        method = "of(Lnet/minecraft/world/item/ItemStack;)Lnet/createmod/ponder/foundation/ui/PonderUI;",
        at = @At("RETURN"),
        require = 0
    )
    private static void ponderer$clearStackReturnFabric(ItemStack item, CallbackInfoReturnable<PonderUI> cir) {
        NbtSceneFilter.clearCurrentStack();
    }

    @Inject(
        method = "of(Lnet/minecraft/world/item/ItemStack;Lnet/createmod/ponder/foundation/PonderTag;)Lnet/createmod/ponder/foundation/ui/PonderUI;",
        at = @At("HEAD"),
        require = 0
    )
    private static void ponderer$captureStackTagHeadFabric(ItemStack item, PonderTag tag, CallbackInfoReturnable<PonderUI> cir) {
        NbtSceneFilter.setCurrentStack(item);
    }

    @Inject(
        method = "of(Lnet/minecraft/world/item/ItemStack;Lnet/createmod/ponder/foundation/PonderTag;)Lnet/createmod/ponder/foundation/ui/PonderUI;",
        at = @At("RETURN"),
        require = 0
    )
    private static void ponderer$clearStackTagReturnFabric(ItemStack item, PonderTag tag, CallbackInfoReturnable<PonderUI> cir) {
        NbtSceneFilter.clearCurrentStack();
    }
}
