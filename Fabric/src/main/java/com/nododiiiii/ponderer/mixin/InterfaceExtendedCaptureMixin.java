package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.interfaces.capture.InterfaceCapture;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ExtendedScreenHandlerType.class)
public class InterfaceExtendedCaptureMixin {
    // The API method name is stable; its Minecraft parameter/return types still need remapping.
    @Inject(method = "create(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/world/inventory/AbstractContainerMenu;", at = @At("HEAD"))
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void ponderer$capture(int id, Inventory inventory, FriendlyByteBuf data, CallbackInfoReturnable<AbstractContainerMenu> ci) {
        if (!InterfaceCapture.isObserving()) return;
        ExtendedScreenHandlerType type = (ExtendedScreenHandlerType)(Object)this;
        if (data.readableBytes() > InterfaceCapture.MAX_EXTRA_BYTES) {
            InterfaceCapture.rejectFactory(id, "ponderer.interface.capture.too_large");
            return;
        }
        byte[] copy = new byte[data.readableBytes()];
        data.getBytes(data.readerIndex(), copy);
        InterfaceCapture.observeFactory(type, id, copy);
    }

    @Inject(method = "create(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/world/inventory/AbstractContainerMenu;", at = @At("RETURN"))
    private void ponderer$finish(int id, Inventory inventory, FriendlyByteBuf data, CallbackInfoReturnable<AbstractContainerMenu> ci) {
        InterfaceCapture.finishFactory(id);
    }
}
