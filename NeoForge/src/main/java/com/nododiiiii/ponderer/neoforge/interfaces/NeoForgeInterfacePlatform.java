package com.nododiiiii.ponderer.neoforge.interfaces;

import com.nododiiiii.ponderer.interfaces.platform.InterfaceSandboxPlatform;
import io.netty.buffer.Unpooled;
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
 * NeoForge bridge for the interface sandbox: public {@code MenuScreens#getScreenFactory},
 * {@code IMenuTypeExtension#create} and the public GUI rectangle getters.
 */
public class NeoForgeInterfacePlatform implements InterfaceSandboxPlatform {

    @Nullable
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Screen createMenuScreen(AbstractContainerMenu menu, Inventory inventory, Component title) {
        MenuType type = menu.getType();
        return (Screen) MenuScreens.getScreenFactory(type)
            .map(factory -> ((MenuScreens.ScreenConstructor) factory).create(menu, inventory, title))
            .orElse(null);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenuWithExtraData(MenuType<?> type, int windowId, Inventory inventory,
                                                         byte[] extraData, RegistryAccess registries) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(extraData), registries);
        try {
            return type.create(windowId, inventory, buffer);
        } finally {
            buffer.release();
        }
    }

    @Nullable
    @Override
    public int[] containerRect(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> container) {
            return new int[]{container.getGuiLeft(), container.getGuiTop(), container.getXSize(), container.getYSize()};
        }
        return null;
    }

    @Nullable
    @Override
    public Slot hoveredSlot(Screen screen) {
        return screen instanceof AbstractContainerScreen<?> container ? container.getSlotUnderMouse() : null;
    }
}
