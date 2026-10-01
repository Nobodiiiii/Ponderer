package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.interfaces.capture.InterfaceCapture;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MenuType.class)
public class InterfaceExtendedCaptureMixin {
    @Inject(method = "create(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/network/RegistryFriendlyByteBuf;)Lnet/minecraft/world/inventory/AbstractContainerMenu;", at = @At("HEAD"))
    private void ponderer$capture(int id, Inventory inventory, RegistryFriendlyByteBuf data, CallbackInfoReturnable<AbstractContainerMenu> ci) {
        if (!InterfaceCapture.isObserving()) return;
        if (data == null) {
            InterfaceCapture.observeFactory((MenuType<?>)(Object)this, id, null);
            return;
        }
        if (data.readableBytes() > InterfaceCapture.MAX_EXTRA_BYTES) {
            InterfaceCapture.rejectFactory(id, "ponderer.interface.capture.too_large");
            return;
        }
        byte[] copy = new byte[data.readableBytes()];
        data.getBytes(data.readerIndex(), copy);
        InterfaceCapture.observeFactory((MenuType<?>)(Object)this, id, copy);
    }

    @Inject(method = "create(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/network/RegistryFriendlyByteBuf;)Lnet/minecraft/world/inventory/AbstractContainerMenu;", at = @At("RETURN"))
    private void ponderer$finish(int id, Inventory inventory, RegistryFriendlyByteBuf data, CallbackInfoReturnable<AbstractContainerMenu> ci) {
        InterfaceCapture.finishFactory(id);
    }
}
