package com.nododiiiii.ponderer.runtime.interfaces.platform;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

import javax.annotation.Nullable;

/**
 * Loader differences the interface sandbox needs: screen factories, extra-data menu
 * factories and the container GUI rectangle. Client-only; loaded lazily through
 * {@link InterfacePlatform} so dedicated servers never resolve it.
 */
public interface InterfaceSandboxPlatform {

    /** Builds the registered screen for {@code menu}, or null when its menu type has no screen. */
    @Nullable
    Screen createMenuScreen(AbstractContainerMenu menu, Inventory inventory, Component title);

    /**
     * Runs the loader's extra-data client factory for {@code type}. Returns null when the
     * loader or menu type has no such factory.
     */
    @Nullable
    AbstractContainerMenu createMenuWithExtraData(MenuType<?> type, int windowId, Inventory inventory,
                                                  byte[] extraData, RegistryAccess registries);

    /** Container GUI rectangle as {left, top, width, height}, or null for non-container screens. */
    @Nullable
    int[] containerRect(Screen screen);

    @Nullable
    Slot hoveredSlot(Screen screen);
}
