package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.interfaces.capture.InterfaceCapture;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MenuType.class)
public class InterfaceMenuCaptureMixin {
    @Inject(method = "create(ILnet/minecraft/world/entity/player/Inventory;)Lnet/minecraft/world/inventory/AbstractContainerMenu;", at = @At("HEAD"))
    private void ponderer$capture(int id, Inventory inventory, CallbackInfoReturnable<AbstractContainerMenu> ci) {
        InterfaceCapture.observeFactory((MenuType<?>)(Object)this, id, null);
    }

    @Inject(method = "create(ILnet/minecraft/world/entity/player/Inventory;)Lnet/minecraft/world/inventory/AbstractContainerMenu;", at = @At("RETURN"))
    private void ponderer$finish(int id, Inventory inventory, CallbackInfoReturnable<AbstractContainerMenu> ci) {
        InterfaceCapture.finishFactory(id);
    }
}
