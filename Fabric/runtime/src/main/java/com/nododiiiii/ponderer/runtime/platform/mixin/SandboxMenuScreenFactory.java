package com.nododiiiii.ponderer.runtime.platform.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.gui.screens.MenuScreens$ScreenConstructor")
public interface SandboxMenuScreenFactory {
    @Invoker("create")
    Screen ponderer$create(AbstractContainerMenu menu, Inventory inventory, Component title);
}
