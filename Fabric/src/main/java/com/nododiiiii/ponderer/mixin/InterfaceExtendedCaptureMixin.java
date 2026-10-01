package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.interfaces.capture.InterfaceCapture;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ExtendedScreenHandlerType.class)
public class InterfaceExtendedCaptureMixin {
    // The API method name is stable; its Minecraft parameter/return types still need remapping.
    @Inject(method = "create(ILnet/minecraft/world/entity/player/Inventory;Ljava/lang/Object;)Lnet/minecraft/world/inventory/AbstractContainerMenu;", at = @At("HEAD"))
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void ponderer$capture(int id, Inventory inventory, Object data, CallbackInfoReturnable<AbstractContainerMenu> ci) {
        if (!InterfaceCapture.isObserving()) return;
        ExtendedScreenHandlerType type = (ExtendedScreenHandlerType)(Object)this;
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(256, InterfaceCapture.MAX_EXTRA_BYTES), inventory.player.registryAccess());
        try {
            type.getPacketCodec().encode(buffer, data);
            byte[] copy = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), copy);
            InterfaceCapture.observeFactory(type, id, copy);
        } catch (RuntimeException ex) {
            com.mojang.logging.LogUtils.getLogger().warn("[Ponderer] Could not capture extended menu data", ex);
            InterfaceCapture.rejectFactory(id, "ponderer.interface.capture.failed");
        } finally {
            buffer.release();
        }
    }

    @Inject(method = "create(ILnet/minecraft/world/entity/player/Inventory;Ljava/lang/Object;)Lnet/minecraft/world/inventory/AbstractContainerMenu;", at = @At("RETURN"))
    private void ponderer$finish(int id, Inventory inventory, Object data, CallbackInfoReturnable<AbstractContainerMenu> ci) {
        InterfaceCapture.finishFactory(id);
    }
}
