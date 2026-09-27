package com.nododiiiii.ponderer.fabric.interfaces;

import com.nododiiiii.ponderer.interfaces.platform.InterfaceSandboxPlatform;
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
 * Fabric bridge for the interface sandbox. {@code MenuScreens#getConstructor} and the container
 * GUI rectangle fields are opened by {@code ponderer.accesswidener}.
 */
public class FabricInterfacePlatform implements InterfaceSandboxPlatform {

    @Nullable
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Screen createMenuScreen(AbstractContainerMenu menu, Inventory inventory, Component title) {
        MenuScreens.ScreenConstructor constructor = MenuScreens.getConstructor((MenuType) menu.getType());
        return constructor == null ? null : (Screen) constructor.create(menu, inventory, title);
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
            return new int[]{container.leftPos, container.topPos, container.imageWidth, container.imageHeight};
        }
        return null;
    }

    @Nullable
    @Override
    public Slot hoveredSlot(Screen screen) {
        return screen instanceof AbstractContainerScreen<?> container ? container.hoveredSlot : null;
    }
}
