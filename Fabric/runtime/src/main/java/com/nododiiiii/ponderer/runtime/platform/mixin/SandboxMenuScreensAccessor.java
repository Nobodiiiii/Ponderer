package com.nododiiiii.ponderer.runtime.platform.mixin;

import java.util.Map;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MenuScreens.class)
public interface SandboxMenuScreensAccessor {
    @Accessor("SCREENS")
    static Map<MenuType<?>, ?> ponderer$screenFactories() { throw new AssertionError(); }
}
