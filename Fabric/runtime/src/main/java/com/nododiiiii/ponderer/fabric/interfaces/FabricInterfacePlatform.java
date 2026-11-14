package com.nododiiiii.ponderer.fabric.interfaces;

import com.nododiiiii.ponderer.runtime.interfaces.platform.InterfaceSandboxPlatform;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

import javax.annotation.Nullable;

/**
 * Fabric bridge with startup-selectable accessors; no unconditional Access Widener.
 */
public class FabricInterfacePlatform implements InterfaceSandboxPlatform {

    @Nullable
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Screen createMenuScreen(AbstractContainerMenu menu, Inventory inventory, Component title) {
        com.nododiiiii.ponderer.runtime.api.RuntimeFeatures.requireInterfaces("create menu screen");
        Object constructor = com.nododiiiii.ponderer.runtime.platform.mixin.SandboxMenuScreensAccessor.ponderer$screenFactories().get(menu.getType());
        return constructor == null ? null : ((com.nododiiiii.ponderer.runtime.platform.mixin.SandboxMenuScreenFactory) constructor).ponderer$create(menu, inventory, title);
    }

    @Nullable
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public AbstractContainerMenu createMenuWithExtraData(MenuType<?> type, int windowId, Inventory inventory,
                                                         byte[] extraData, RegistryAccess registries) {
        if (!(type instanceof ExtendedScreenHandlerType extended)) {
            return null;
        }
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(extraData), registries);
        try {
            Object data = extended.getPacketCodec().decode(buffer);
            return (AbstractContainerMenu) extended.create(windowId, inventory, data);
        } finally {
            buffer.release();
        }
    }

    @Nullable
    @Override
    public int[] containerRect(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> container) {
            var access = (com.nododiiiii.ponderer.runtime.platform.mixin.SandboxContainerScreenAccessor) container;
            return new int[]{access.ponderer$left(), access.ponderer$top(), access.ponderer$width(), access.ponderer$height()};
        }
        return null;
    }

    @Nullable
    @Override
    public Slot hoveredSlot(Screen screen) {
        return screen instanceof AbstractContainerScreen<?> container ? ((com.nododiiiii.ponderer.runtime.platform.mixin.SandboxContainerScreenAccessor) container).ponderer$hoveredSlot() : null;
    }
}
